package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoEliminato
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 (EX-4, EX-5): [SessioneProgettoImpl.elimina] of the project open in this session, over the real single
 * composition ([AmbienteProgetto]): closed and deleted when its queue is idle, refused and left open otherwise.
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
}
