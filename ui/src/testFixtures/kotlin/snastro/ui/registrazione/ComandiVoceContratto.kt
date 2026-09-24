package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [ComandiVoce] (ADR 0017 §3, AC-411..AC-415): the fake proves it green on
 * its own (D1); `avvio-parlanti`'s per-project adapter subclasses it (D2, AC-418). [con] builds the port
 * running its commands in [progetto] (the project scope) with [esecutore] as the command body — a body
 * that suspends until the test lets it go stands for a command waiting on the native Mutex. ADR 0019 §5:
 * `nominaFrase` has the same scope, pending state (keyed by [FraseRef]) and cancellation, with
 * `esecutoreFrase` as its body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ComandiVoceContratto {
    protected abstract fun con(
        progetto: CoroutineScope,
        clock: Clock,
        esecutoreFrase: suspend (FraseRef, PassiNominaFrase) -> Esito<Unit> = { _, _ -> Esito.Ok(Unit) },
        esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    ): ComandiVoce

    private val voce = VoceRef(RegistrazioneId("id-1"), VoceId(1))
    private val comando = ComandoVoce.Conferma(voce, ParlanteId("p-1"))
    private val orologio = Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC)

    // Not `backgroundScope`: `advanceUntilIdle` does not run background-only work (kotlinx-coroutines-test).
    private fun TestScope.progetto(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler))

    @Test
    fun `esegui restituisce l esito del comando e alla fine non resta nulla in corso`() = runTest {
        val porta = con(progetto(), orologio) { Esito.Ok(Unit) }
        assertEquals(Esito.Ok(Unit), porta.esegui(comando))
        advanceUntilIdle()
        assertTrue(porta.stato.value.isEmpty())
    }

    @Test
    fun `mentre il comando e in corso stato ne espone la VoceRef con l istante di avvio`() = runTest {
        val cancello = kotlinx.coroutines.CompletableDeferred<Unit>()
        val porta = con(progetto(), orologio) { cancello.await(); Esito.Ok(Unit) }
        val esito = async { porta.esegui(comando) }
        runCurrent()
        assertEquals(StatoComando(orologio.instant()), porta.stato.value[voce])
        cancello.complete(Unit)
        assertEquals(Esito.Ok(Unit), esito.await())
    }

    @Test
    fun `annulla fa restituire null, il comando vede l annullamento e lo stato si svuota`() = runTest {
        var visto = false
        val porta = con(progetto(), orologio) {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                visto = true
            }
        }
        val esito = async { porta.esegui(comando) }
        runCurrent()
        porta.annulla(voce)
        assertNull(esito.await())
        advanceUntilIdle()
        assertTrue(visto)
        assertTrue(porta.stato.value.isEmpty())
    }

    @Test
    fun `cancellare chi aspetta non annulla il comando che resta in corso nello scope del progetto`() = runTest {
        val cancello = kotlinx.coroutines.CompletableDeferred<Unit>()
        var completato = false
        val porta = con(progetto(), orologio) {
            cancello.await()
            completato = true
            Esito.Ok(Unit)
        }
        val schermata = launch { porta.esegui(comando) }
        runCurrent()
        schermata.cancelAndJoin()
        assertEquals(setOf(voce), porta.stato.value.keys)
        cancello.complete(Unit)
        advanceUntilIdle()
        assertTrue(completato)
        assertTrue(porta.stato.value.isEmpty())
    }

    @Test
    fun `un comando che lancia diventa un Errore, lo stato si svuota e la porta resta utilizzabile`() = runTest {
        var chiamate = 0
        val porta = con(progetto(), orologio) {
            chiamate++
            if (chiamate == 1) throw IllegalStateException("sorgente audio illeggibile")
            Esito.Ok(Unit)
        }
        assertEquals(ErroreComandoVoce.NonRiuscito, assertIs<Esito.Errore>(porta.esegui(comando)).errore)
        advanceUntilIdle()
        assertTrue(porta.stato.value.isEmpty())
        assertEquals(Esito.Ok(Unit), porta.esegui(comando))
    }

    private val frase = FraseRef(RegistrazioneId("id-1"), SegmentoId(3))
    private val passi = PassiNominaFrase.SoloConferma

    @Test
    fun `nominaFrase restituisce l esito dei passi ricevuti e alla fine non resta nulla in corso`() = runTest {
        var ricevuti: Pair<FraseRef, PassiNominaFrase>? = null
        val porta = con(progetto(), orologio, esecutoreFrase = { r, p ->
            ricevuti = r to p
            Esito.Ok(Unit)
        }) { Esito.Ok(Unit) }
        assertEquals(Esito.Ok(Unit), porta.nominaFrase(frase.registrazioneId, frase.segmentoId, passi))
        advanceUntilIdle()
        assertEquals(frase to passi, ricevuti)
        assertTrue(porta.statoFrasi.value.isEmpty())
    }

    @Test
    fun `mentre nominaFrase e in corso statoFrasi espone la frase e annullaFrase fa restituire null`() = runTest {
        var visto = false
        val porta = con(progetto(), orologio, esecutoreFrase = { _, _ ->
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                visto = true
            }
        }) { Esito.Ok(Unit) }
        val esito = async { porta.nominaFrase(frase.registrazioneId, frase.segmentoId, passi) }
        runCurrent()
        assertEquals(StatoComando(orologio.instant()), porta.statoFrasi.value[frase])
        assertTrue(porta.stato.value.isEmpty())
        porta.annullaFrase(frase)
        assertNull(esito.await())
        advanceUntilIdle()
        assertTrue(visto)
        assertTrue(porta.statoFrasi.value.isEmpty())
    }

    @Test
    fun `nominaFrase che lancia diventa un Errore e la porta resta utilizzabile`() = runTest {
        var chiamate = 0
        val porta = con(progetto(), orologio, esecutoreFrase = { _, _ ->
            chiamate++
            if (chiamate == 1) throw IllegalStateException("sorgente audio illeggibile")
            Esito.Ok(Unit)
        }) { Esito.Ok(Unit) }
        val errore = porta.nominaFrase(frase.registrazioneId, frase.segmentoId, passi)
        assertEquals(ErroreComandoVoce.NonRiuscito, assertIs<Esito.Errore>(errore).errore)
        advanceUntilIdle()
        assertEquals(Esito.Ok(Unit), porta.nominaFrase(frase.registrazioneId, frase.segmentoId, passi))
    }
}
