package snastro.avvio.accettazione

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.ui.progetti.ProgettiPresenter
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R0 · elimina solo i contenuti di snastro (EX-9, EX-10): the list's presenter over the REAL [SessioneProgettoImpl]
 * and real project folders under `~/Documents/snastro` — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. EX-10's undeletable file is a real one: its
 * folder loses the write permission. The presenter runs on [BancoS1]'s dispatcher, shared with `runTest` and advanced
 * explicitly, its scope cancelled in teardown; the session, which registers a closed project on real threads, is
 * built by [conSessione]: closed and its scope cancelled when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaSoloContenutiSnastroTest : BancoS1() {
    private val skatefest: Path by lazy { documenti.resolve("skatefest.snastro") }

    @Test
    fun `EX-9 eliminare skatefest cancella solo cio che ha creato snastro, appunti pdf resta e lo si dice`() =
        conSessione(registro, dispatcher) { sessione ->
            val presenter = listaConSkatefestEMangu(sessione)
            Files.writeString(skatefest.resolve("progetto.db-wal"), "")
            Files.writeString(skatefest.resolve("progetto.db-shm"), "")
            Files.writeString(skatefest.resolve("audio/incontro.wav"), "RIFF")
            val fuori = Files.writeString(Files.createDirectories(casa.resolve("Music")).resolve("prova.wav"), "RIFF")
            Files.createSymbolicLink(skatefest.resolve("audio/collegamento.wav"), fuori)
            Files.writeString(skatefest.resolve("appunti.pdf"), "%PDF")

            elimina(presenter, "skatefest")

            val rimasti = Files.list(skatefest).use { file -> file.map { it.fileName.toString() }.toList() }
            assertEquals(listOf("appunti.pdf"), rimasti)
            assertTrue(Files.exists(fuori), "il link simbolico non si segue: il file a cui punta resta")
            assertEquals(
                "Il progetto è stato eliminato. Nella cartella ~/Documents/snastro/skatefest.snastro " +
                    "sono rimasti file non creati da snastro.",
                dati(presenter).avvisoEliminazione,
            )
            assertEquals(listOf("mangu 2"), nomi(presenter))
            assertNull(dati(presenter).conferma)
        }

    @Test
    fun `EX-10 un file di audio che non si puo cancellare lascia la cartella, lo si dice e la voce sparisce`() =
        conSessione(registro, dispatcher) { sessione ->
            val presenter = listaConSkatefestEMangu(sessione)
            val audio = skatefest.resolve("audio")
            Files.writeString(audio.resolve("incontro.wav"), "RIFF")
            assertTrue(audio.toFile().setWritable(false), "il test presuppone di poter revocare il permesso")
            try {
                elimina(presenter, "skatefest")
            } finally {
                audio.toFile().setWritable(true)
            }

            assertTrue(Files.isDirectory(skatefest, LinkOption.NOFOLLOW_LINKS))
            assertEquals(
                "Il progetto è stato eliminato solo in parte: resta la cartella ~/Documents/snastro/skatefest.snastro.",
                dati(presenter).avvisoEliminazione,
            )
            assertEquals(listOf("mangu 2"), nomi(presenter))
            assertNull(dati(presenter).conferma)
        }

    /** "skatefest" and "mangu 2" on disk and in the registry, closed, then the list loaded by the presenter. */
    private fun TestScope.listaConSkatefestEMangu(sessione: SessioneProgettoImpl): ProgettiPresenter {
        for (nome in listOf("skatefest", "mangu 2")) creaChiuso(sessione, nome)
        return lista(sessione)
    }
}
