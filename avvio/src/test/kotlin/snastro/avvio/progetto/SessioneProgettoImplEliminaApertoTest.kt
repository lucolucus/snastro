package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.DatabaseProgetto
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoEliminato
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * R0 (EX-4, EX-5): [SessioneProgettoImpl.elimina] of the project open in this session, over the real single
 * composition ([AmbienteProgetto]): closed and deleted when its queue is idle, refused and left open otherwise; closed
 * but not deleted, and said so, when its shutdown outlives the deadline.
 */
class SessioneProgettoImplEliminaApertoTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `il progetto aperto con la coda ferma si chiude, si cancella ed esce dal registro`() {
        AmbienteProgetto(radice).use { ambiente ->
            val percorso = ambiente.progetto.percorso
            attendiFinche(timeout = 10.seconds, messaggio = "progetto registrato") { ambiente.registro.elenco().any() }

            val eliminato = ambiente.sessione.elimina(percorso).atteso()

            assertEquals(ProgettoEliminato.Completamente, eliminato)
            assertNull(ambiente.sessione.corrente.value)
            assertNull(ambiente.sessione.collaboratoriCorrenti())
            assertFalse(Files.exists(Path.of(percorso)))
            assertTrue(ambiente.registro.elenco().isEmpty())
        }
    }

    @Test
    fun `una trascrizione in attesa nella coda rifiuta l eliminazione e lascia il progetto aperto`() {
        // Models not ready: the Elaborazione stays waiting, never running.
        AmbienteProgetto(radice, modelliPronti = { false }).use { ambiente ->
            val registrazione = ambiente.importaEAvvia()
            assertEquals(StatoElaborazioneVista.IN_ATTESA, ambiente.stato(registrazione))

            val errore = ambiente.sessione.elimina(ambiente.progetto.percorso).erroreAtteso<ErroreSessione>()

            assertEquals(ErroreSessione.ElaborazioneInCorso, errore)
            assertEquals(ambiente.progetto, ambiente.sessione.corrente.value)
            assertTrue(Files.isRegularFile(Path.of(ambiente.progetto.percorso, "progetto.db")))
        }
    }

    @Test
    fun `una chiusura oltre la scadenza non cancella niente, lo dice e lascia eliminare dopo dalla lista`() {
        val chiusuraIniziata = CountDownLatch(1)
        val fineChiusura = CountDownLatch(1)
        val chiusuraLenta: (DatabaseProgetto) -> Unit = { db ->
            chiusuraIniziata.countDown()
            fineChiusura.await(ATTESA_S, TimeUnit.SECONDS) // a worker still on the database past the deadline
            db.chiudi()
        }
        AmbienteProgetto(radice, chiudiDatabase = chiusuraLenta, scadenzaArresto = SCADENZA_BREVE).use { ambiente ->
            val percorso = ambiente.progetto.percorso
            attendiFinche(timeout = 10.seconds, messaggio = "progetto registrato") { ambiente.registro.elenco().any() }

            val errore = ambiente.sessione.elimina(percorso).erroreAtteso<ErroreSessione>()

            assertTrue(chiusuraIniziata.await(ATTESA_S, TimeUnit.SECONDS), "precondizione: la chiusura e' partita")
            assertEquals(ErroreSessione.ProgettoChiusoNonEliminato, errore)
            assertNull(ambiente.sessione.corrente.value, "il progetto e' chiuso: la UI torna a S1")
            assertTrue(Files.isRegularFile(Path.of(percorso, "progetto.db")), "niente cancellato")
            assertEquals(listOf(percorso), ambiente.registro.elenco().map { it.percorso }, "ancora nella lista")

            fineChiusura.countDown() // the worker ends: the `.lock` is released
            attendiFinche(timeout = 10.seconds, messaggio = "eliminato dalla lista dopo la chiusura") {
                ambiente.sessione.elimina(percorso) is Esito.Ok
            }
            assertFalse(Files.exists(Path.of(percorso)))
        }
    }

    private companion object {
        const val ATTESA_S = 10L
        val SCADENZA_BREVE = 300.milliseconds
    }
}
