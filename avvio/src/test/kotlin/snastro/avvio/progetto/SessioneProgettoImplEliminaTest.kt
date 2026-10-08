package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.ui.ErroreSessione
import snastro.ui.ProgettoEliminato
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A registry whose `rimuovi` always fails; everything else is [RegistroProgettiFinta]'s. */
private class RegistroCheNonRimuove(private val delegato: RegistroProgetti = RegistroProgettiFinta()) :
    RegistroProgetti by delegato {
    override fun rimuovi(percorso: String): Unit = error("registro non scrivibile")
}

/** R0: [SessioneProgettoImpl.elimina] touches only a snastro project folder, and only what snastro created there. */
class SessioneProgettoImplEliminaTest {
    @TempDir
    lateinit var cartella: Path

    private fun SessioneProgettoImpl.progettoChiuso(registro: RegistroProgetti): Path {
        val creato = crea(cartella.toString(), "skatefest").atteso()
        chiudi()
        attendiFinche(timeout = 10.seconds, messaggio = "skatefest registrato") {
            registro.elenco().any { it.percorso == creato.percorso }
        }
        return Path.of(creato.percorso)
    }

    @Test
    fun `una cartella che non e un progetto snastro non si tocca e resta in lista`() {
        val registro = RegistroProgettiFinta()
        conSessioneDiProva(registro) { sessione ->
            val progetto = sessione.progettoChiuso(registro)
            Files.delete(progetto.resolve("progetto.db"))

            val errore = sessione.elimina(progetto.toString()).erroreAtteso<ErroreSessione>()

            assertEquals(ErroreSessione.CartellaNonValida, errore)
            assertTrue(Files.isDirectory(progetto.resolve("audio")))
            assertTrue(registro.elenco().any { it.percorso == progetto.toString() })
        }
    }

    @Test
    fun `se il registro non toglie la voce la cartella resta intera`() {
        val registro = RegistroCheNonRimuove()
        conSessioneDiProva(registro) { sessione ->
            val progetto = sessione.progettoChiuso(registro)

            assertFails { sessione.elimina(progetto.toString()) }

            assertTrue(Files.isRegularFile(progetto.resolve("progetto.db")))
            assertTrue(Files.isDirectory(progetto.resolve("audio")))
        }
    }

    @Test
    fun `una cartella di snastro che e un link simbolico si cancella senza seguirlo`() {
        val registro = RegistroProgettiFinta()
        conSessioneDiProva(registro) { sessione ->
            val progetto = sessione.progettoChiuso(registro)
            val fuori = Files.createDirectories(cartella.resolve("altrove"))
            val fileFuori = Files.writeString(fuori.resolve("registrazione.wav"), "RIFF")
            Files.walk(progetto.resolve("audio")).sorted(Comparator.reverseOrder()).forEach(Files::delete)
            Files.createSymbolicLink(progetto.resolve("audio"), fuori)

            val eliminato = sessione.elimina(progetto.toString()).atteso()

            assertEquals(ProgettoEliminato.Completamente, eliminato)
            assertFalse(Files.exists(progetto))
            assertTrue(Files.exists(fileFuori))
            assertFalse(registro.elenco().any { it.percorso == progetto.toString() })
        }
    }
}
