package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.trascrizione.AdattatoriMl
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.Riconoscimento
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VadFinta
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.ErroreSessione
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * fix-batch-16: closing a project while its Elaborazione runs, over the REAL single composition
 * ([AmbienteProgetto]: real queue thread, real SQLite project folder, real `.lock`).
 *
 * - MED-1(b): a pipeline stuck in a native call that IGNORES the interrupt (the diarization) outlives
 *   the ONE shutdown deadline ([snastro.avvio.ArrestoProgetto]) — the database stays open and the `.lock` held
 *   until it actually ends; `corrente`
 *   is null at once; a reopen meanwhile is 'progetto gia' aperto'. MED-1(c): once the native call
 *   returns, the Allineatore stops before its first ASR chunk.
 * - MED-2: the ML adapters are built once per open project, never shared across two.
 */
class ChiusuraProgettoTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `fix-batch-16 un lavoratore che ignora l interruzione tiene database e lock finche non finisce`() {
        val diarizzatore = DiarizzatoreCheIgnoraInterruzione()
        val riconoscitore = RiconoscitoreCheConta()
        val ambiente = AmbienteProgetto(
            radice,
            diarizzatore = diarizzatore,
            riconoscitore = riconoscitore,
            scadenzaArresto = SCADENZA_BREVE,
        )
        ambiente.use {
            val id = ambiente.importa()
            val coda = ambiente.coda
            val percorso = ambiente.progetto.percorso
            ambiente.avviaElaborazione(id)
            assertTrue(diarizzatore.inCorso.await(ATTESA_S, TimeUnit.SECONDS))

            ambiente.sessione.chiudi() // returns after ArrestoProgetto's deadline, the worker still alive

            assertNull(ambiente.sessione.corrente.value, "la UI torna subito a S1")
            assertFalse(coda.lavoro.isCompleted, "precondizione: il lavoratore e' ancora vivo")
            assertEquals(
                ErroreSessione.ProgettoGiaAperto,
                ambiente.sessione.apri(percorso).erroreAtteso<ErroreSessione>(),
                "database e lock restano al lavoratore vivo",
            )

            diarizzatore.fine.countDown() // the native call returns (interrupt flag still set)
            attendiFinche(timeout = 10.seconds, messaggio = "fine del lavoratore orfano") { coda.lavoro.isCompleted }
            attendiFinche(timeout = 10.seconds, messaggio = "lock rilasciato alla fine del lavoratore") {
                ambiente.sessione.apri(percorso) is Esito.Ok
            }

            assertEquals(0, riconoscitore.chiamate, "l'Allineatore si ferma prima del primo pezzo ASR")
            attendiFinche(timeout = 10.seconds, messaggio = "recupero dell'in_corso interrotto") {
                ambiente.stato(id) == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", ambiente.vistaDi(id).motivoFallimento)
        }
    }

    @Test
    fun `fix-batch-16 MED-2 gli adattatori ML sono costruiti per ogni progetto aperto`() {
        val costruiti = mutableListOf<AdattatoriMl>()
        AmbienteProgetto(
            radice,
            adattatoriMl = {
                AdattatoriMl(DiarizzatoreFinta(), RiconoscitoreParlatoFinta(), VadFinta()).also(costruiti::add)
            },
        ).use { ambiente ->
            ambiente.sessione.chiudi()
            ambiente.sessione.apri(ambiente.progetto.percorso).atteso()

            assertEquals(2, costruiti.size, "uno per apertura")
            assertNotSame(costruiti[0], costruiti[1])
        }
    }

    /**
     * Like a sherpa diarization (one long JNI call): the thread's interrupt is NOT honoured — it waits
     * for [fine] anyway, and returns with the interrupt flag still set, as a native call would.
     */
    private class DiarizzatoreCheIgnoraInterruzione(private val delegato: Diarizzatore = DiarizzatoreFinta()) :
        Diarizzatore {
        val inCorso = CountDownLatch(1)
        val fine = CountDownLatch(1)

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            inCorso.countDown()
            var interrotto = false
            while (true) {
                try {
                    fine.await()
                    break
                } catch (ignored: InterruptedException) {
                    interrotto = true
                }
            }
            if (interrotto) Thread.currentThread().interrupt()
            return delegato.diarizza(c, numeroPersone)
        }
    }

    private class RiconoscitoreCheConta(private val delegato: RiconoscitoreParlato = RiconoscitoreParlatoFinta()) :
        RiconoscitoreParlato {
        @Volatile var chiamate = 0

        override fun riconosci(c: CampioniAudio): Riconoscimento = delegato.riconosci(c).also { chiamate++ }
    }

    private companion object {
        const val ATTESA_S = 10L
        val SCADENZA_BREVE = 500.milliseconds
    }
}
