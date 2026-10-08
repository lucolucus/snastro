package snastro.avvio.accettazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.atteso
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.conScopeDiProva
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiUiStato
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 · elimina solo i contenuti di snastro (EX-9, EX-10): the list's presenter over the REAL [SessioneProgettoImpl]
 * and real project folders under `~/Documents/snastro` — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. EX-10's undeletable file is a real one: its
 * folder loses the write permission. The presenter runs on a [StandardTestDispatcher] shared with `runTest` and
 * advanced explicitly, its scope cancelled in teardown; the session, which registers a closed project on real
 * threads, gets its scope from [conScopeDiProva], cancelled when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaSoloContenutiSnastroTest {
    @TempDir
    lateinit var casa: Path

    private val documenti: Path by lazy { casa.resolve("Documents/snastro") }
    private val skatefest: Path by lazy { documenti.resolve("skatefest.snastro") }

    private val registro = RegistroProgettiFinta()
    private val dispatcher = StandardTestDispatcher()
    private val scopePresenter = CoroutineScope(SupervisorJob() + dispatcher)

    @AfterEach
    fun chiudi() = scopePresenter.cancel()

    /** Runs [blocco] under `runTest` with a real session over [registro], then closes it and cancels its scope. */
    private fun conSessione(blocco: suspend TestScope.(SessioneProgettoImpl) -> Unit) = conScopeDiProva { scope ->
        val sessione = SessioneProgettoImpl(
            registro,
            GeneratoreIdFinto(),
            Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC),
            scope,
            componentiDiProva(),
        )
        try {
            runTest(dispatcher) { blocco(sessione) }
        } finally {
            sessione.chiudi()
        }
    }

    @Test
    fun `EX-9 eliminare skatefest cancella solo cio che ha creato snastro, appunti pdf resta e lo si dice`() =
        conSessione { sessione ->
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
        conSessione { sessione ->
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

    private fun TestScope.elimina(presenter: ProgettiPresenter, nome: String) {
        presenter.azioni.chiediElimina(dati(presenter).progetti.single { it.nome == nome })
        presenter.azioni.confermaElimina(nome)
        advanceUntilIdle()
    }

    /** "skatefest" and "mangu 2" on disk and in the registry, closed, then the list loaded by the presenter. */
    private fun TestScope.listaConSkatefestEMangu(sessione: SessioneProgettoImpl): ProgettiPresenter {
        for (nome in listOf("skatefest", "mangu 2")) {
            val creato = sessione.crea(documenti.toString(), nome).atteso()
            sessione.chiudi()
            attendiFinche(timeout = 10.seconds, messaggio = "$nome registrato") {
                registro.elenco().any { it.percorso == creato.percorso }
            }
        }
        return ProgettiPresenter(scopePresenter, dispatcher, ElencoProgetti(registro), sessione, casa.toString())
            .also { advanceUntilIdle() }
    }

    private fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)

    private fun nomi(presenter: ProgettiPresenter): List<String> = dati(presenter).progetti.map(ProgettoVista::nome)
}
