package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * L260 (D-0062): an S3 Revisione command that COMMITTED but whose after-commit subscriber then failed is a success —
 * reload, no error message; L253: a reload that finds no Trascritto still recomputes the cross-Parte pair.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatoVociConsegnaDopoCommitTest {
    private fun TestScope.ambiente() =
        AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))

    private fun TestScope.avvia(a: AmbienteVoci): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    private fun confermatoMaSeguitoFallito(a: AmbienteVoci, applica: (TrascrittoView) -> TrascrittoView): Esito<Unit> {
        a.vista = applica(a.vista)
        throw ConsegnaDopoCommitFallita(IllegalStateException("abbonato dopo-commit fallito"))
    }

    @Test
    fun `L260 un unione confermata con un abbonato dopo-commit fallito ricarica senza messaggio di errore`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { c ->
            val u = c as UnisciVoci
            confermatoMaSeguitoFallito(a) { v -> spostato(v, segmento = 4, voce = u.sopravvive) }
        }

        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()

        assertNull(presenter.dati.errore)
        assertEquals(listOf(V1, V2), assertNotNull(presenter.dati.pannello).carte.map { it.voceId })
    }

    @Test
    fun `L260 una riassegnazione di piu Segmenti confermati uno a uno li sposta tutti senza errore`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { c ->
            val r = c as RiassegnaSegmento
            confermatoMaSeguitoFallito(a) { v -> spostato(v, r.segmento.numero, checkNotNull(r.destinazione)) }
        }
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))

        presenter.azioni.riassegnaA(V2)
        advanceUntilIdle()

        assertEquals(2, a.revisioni.size, "il primo seguito fallito non ferma il secondo Segmento")
        assertNull(presenter.dati.errore)
        assertEquals(listOf(V2, V3), assertNotNull(presenter.dati.pannello).carte.map { it.voceId })
    }

    @Test
    fun `L275 un Dividi confermato con un abbonato dopo-commit fallito ricarica senza errore`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { confermatoMaSeguitoFallito(a) { v -> spostato(v, segmento = 1, voce = VoceId(4)) } }
        presenter.azioni.selezionaSegmento(SegmentoId(1))

        presenter.azioni.dividiVoce()
        advanceUntilIdle()

        assertIs<DividiVoce>(a.revisioni.single())
        assertNull(presenter.dati.errore)
        assertEquals(VoceId(4), presenter.dati.segmenti.single { it.segmentoId == SegmentoId(1) }.voceId)
    }

    @Test
    fun `L275 un Togli conferma confermato con un abbonato dopo-commit fallito ricarica senza errore`() = runTest {
        val a = ambiente()
        a.vista = a.vista.copy(segmenti = a.vista.segmenti.map { it.copy(confermato = it.segmentoId == SegmentoId(1)) })
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { c ->
            val conferma = c as ConfermaSegmento
            confermatoMaSeguitoFallito(a) { v ->
                val tolto = conferma.segmento
                v.copy(segmenti = v.segmenti.map { it.copy(confermato = it.confermato && it.segmentoId != tolto) })
            }
        }
        presenter.azioni.selezionaSegmento(SegmentoId(1))

        presenter.azioni.togliConferma()
        advanceUntilIdle()

        assertIs<ConfermaSegmento>(a.revisioni.single())
        assertNull(presenter.dati.errore)
        assertFalse(presenter.dati.segmenti.single { it.segmentoId == SegmentoId(1) }.confermato)
    }

    @Test
    fun `L274 un guasto al secondo Segmento rilegge cio che il primo ha gia spostato e mostra l errore`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { c ->
            check((c as RiassegnaSegmento).segmento != SegmentoId(3)) { "guasto SQL" }
            Esito.Ok(Unit)
        }
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))

        presenter.azioni.riassegnaA(V2)
        advanceUntilIdle()

        assertEquals(MESSAGGIO_ERRORE_GENERICO, presenter.dati.errore)
        assertEquals(V2, presenter.dati.segmenti.single { it.segmentoId == SegmentoId(1) }.voceId, "riletto")
    }

    @Test
    fun `L274 se la nuova Voce non si rilegge dopo il primo Segmento ci si ferma invece di aprirne una per Segmento`() =
        runTest {
            val a = ambiente()
            val presenter = avvia(a)
            advanceUntilIdle()
            a.esitoRevisione = {
                a.trascrittoSparito = true
                Esito.Ok(Unit)
            }
            presenter.azioni.selezionaSegmento(SegmentoId(1))
            presenter.azioni.selezionaSegmento(SegmentoId(3))

            presenter.azioni.riassegnaA(null)
            advanceUntilIdle()

            assertEquals(1, a.revisioni.size, "nessuna seconda nuova Voce")
            assertEquals(MESSAGGIO_ERRORE_GENERICO, presenter.dati.errore)
        }

    @Test
    fun `L260 un guasto che non e una consegna dopo-commit resta il messaggio generico`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = { throw IllegalStateException("guasto SQL") }

        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()

        assertEquals(MESSAGGIO_ERRORE_GENERICO, presenter.dati.errore)
    }

    @Test
    fun `L253 una rilettura dopo la Revisione senza Trascritto ricalcola comunque la coppia tra parti`() = runTest {
        val coppia = CoppiaTraParti(
            voceA = V1,
            parteA = 1,
            estrattoA = EstrattoRef(RegistrazioneId("id-1"), listOf(IntervalloMs(0, 500))),
            voceB = V3,
            parteB = 2,
            estrattoB = EstrattoRef(RegistrazioneId("id-2"), listOf(IntervalloMs(1_000, 1_500))),
        )
        val a = ambiente().apply { traParti = listOf(coppia) }
        val presenter = avvia(a)
        advanceUntilIdle()
        a.esitoRevisione = {
            a.trascrittoSparito = true
            Esito.Ok(Unit)
        }

        presenter.azioni.unisci(V1, V2)
        advanceUntilIdle()

        assertEquals(2, a.chiamateTraParti.size)
        assertEquals(coppia, assertNotNull(presenter.dati.pannello).traParti)
    }
}
