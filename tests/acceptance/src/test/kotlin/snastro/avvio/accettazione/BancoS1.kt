package snastro.avvio.accettazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.SEZIONI_SHELL
import snastro.avvio.progetto.elencoProgetti
import snastro.kernel.atteso
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.ui.ProgettoAperto
import snastro.ui.SessioneProgetto
import snastro.ui.ShellPresenter
import snastro.ui.ShellUiStato
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiUiStato
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/** The files an open database makes and drops on its own. */
private val FILE_DEL_WAL = setOf("progetto.db-wal", "progetto.db-shm")

/**
 * The bench of the S1 and shell acceptance tests: the user's home [casa] (a temporary folder, projects under
 * `~/Documents/snastro`), the per-user registry [registro] shared by the session and the list, the
 * [StandardTestDispatcher] shared with `runTest` (pass both to [conSessione]) and the presenters' scope — a
 * `CoroutineScope(SupervisorJob() + dispatcher)`, never `backgroundScope`, cancelled after each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BancoS1 {
    @TempDir
    lateinit var casa: Path

    protected val documenti: Path by lazy { casa.resolve("Documents/snastro") }
    protected val registro = RegistroProgettiFinta()
    protected val dispatcher = StandardTestDispatcher()
    private val scopePresenter = CoroutineScope(SupervisorJob() + dispatcher)

    @AfterEach
    fun cancellaScopePresenter() = scopePresenter.cancel()

    /** [nome] created under [documenti], closed, and in [registro]. */
    protected fun creaChiuso(sessione: SessioneProgetto, nome: String): ProgettoAperto {
        val creato = sessione.crea(documenti.toString(), nome).atteso()
        sessione.chiudi()
        attendiFinche(timeout = 10.seconds, messaggio = "$nome registrato") {
            registro.elenco().any { it.percorso == creato.percorso }
        }
        return creato
    }

    /** Opens S1: a fresh presenter over [sessione] and [casa], as the app builds it each time S1 is shown, loaded. */
    protected fun TestScope.lista(sessione: SessioneProgetto): ProgettiPresenter =
        ProgettiPresenter(scopePresenter, dispatcher, elencoProgetti(registro), sessione, casa.toString())
            .also { advanceUntilIdle() }

    /** The shell over [sessione] and [casa], showing the project open there. */
    protected fun TestScope.shell(sessione: SessioneProgetto): ShellPresenter =
        ShellPresenter(scopePresenter, dispatcher, sessione, SEZIONI_SHELL, casa.toString()).also {
            advanceUntilIdle()
            assertIs<ShellUiStato.ConProgetto>(it.stato.value)
        }

    /** S1's "Elimina" on [nome], [nome] typed, confirmed. */
    protected fun TestScope.elimina(presenter: ProgettiPresenter, nome: String) {
        presenter.azioni.chiediElimina(progetto(presenter, nome))
        presenter.azioni.confermaElimina(nome)
        advanceUntilIdle()
    }

    /** The shell's header menu → Elimina progetto, [nome] typed, confirmed. */
    protected fun TestScope.elimina(shell: ShellPresenter, nome: String) {
        shell.azioni.chiediElimina()
        shell.azioni.confermaElimina(nome)
        advanceUntilIdle()
    }

    protected fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)

    protected fun nomi(presenter: ProgettiPresenter): List<String> = dati(presenter).progetti.map(ProgettoVista::nome)

    protected fun progetto(presenter: ProgettiPresenter, nome: String): ProgettoVista =
        dati(presenter).progetti.single { it.nome == nome }
}

/** Waits until every one of [nomi] is in [registro]: a project is registered on the session's own threads. */
internal fun attendiRegistrati(registro: RegistroProgetti, vararg nomi: String) =
    attendiFinche(timeout = 10.seconds, messaggio = "${nomi.toList()} registrati") {
        registro.elenco().map { it.nome }.containsAll(nomi.toList())
    }

/**
 * Every path under [cartella], relative to it and sorted, but the database's WAL files: an open database makes and
 * drops them, so they are skipped even when they vanish during the walk — what "nothing deleted" compares.
 */
internal fun contenuto(cartella: Path): List<String> {
    val percorsi = mutableListOf<String>()
    Files.walkFileTree(
        cartella,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != cartella) percorsi += cartella.relativize(dir).toString()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!fileDelWal(file)) percorsi += cartella.relativize(file).toString()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                if (exc is NoSuchFileException && fileDelWal(file)) FileVisitResult.CONTINUE else throw exc
        },
    )
    return percorsi.sorted()
}

private fun fileDelWal(file: Path) = file.fileName.toString() in FILE_DEL_WAL
