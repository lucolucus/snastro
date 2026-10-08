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
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.avvio.progetto.elencoProgetti
import snastro.kernel.atteso
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiUiStato
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 · progetto non trovato (EX-7, EX-8, EX-12, EX-13): the list's presenter over the REAL [SessioneProgettoImpl], the
 * real folder check of the list ([elencoProgetti]) and a real project folder `~/Documents/snastro/test.snastro`, moved
 * away to `~/Desktop/` and back — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. "Opening the list" is a fresh presenter, as
 * S1 is each time it is shown. The presenters run on a [StandardTestDispatcher] shared with `runTest` and advanced
 * explicitly, their scope cancelled in teardown; the session is built by [conSessione].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgettoNonTrovatoTest {
    @TempDir
    lateinit var casa: Path

    private val documenti: Path by lazy { casa.resolve("Documents/snastro") }
    private val test: Path by lazy { documenti.resolve("test.snastro") }
    private val spostata: Path by lazy { Files.createDirectories(casa.resolve("Desktop")).resolve("test.snastro") }

    private val registro = RegistroProgettiFinta()
    private val dispatcher = StandardTestDispatcher()
    private val scopePresenter = CoroutineScope(SupervisorJob() + dispatcher)

    @AfterEach
    fun chiudi() = scopePresenter.cancel()

    @Test
    fun `EX-7 test spostata sulla Scrivania appare nella lista come Cartella non trovata`() =
        conSessione(registro, dispatcher) { sessione ->
            registraTest(sessione)
            Files.move(test, spostata)

            val presenter = apriLista(sessione)

            assertFalse(progetto(presenter, "test").cartellaTrovata)
        }

    @Test
    fun `EX-8 Togli dalla lista su test la toglie dalla lista e lascia intatta la cartella sulla Scrivania`() =
        conSessione(registro, dispatcher) { sessione ->
            registraTest(sessione)
            Files.move(test, spostata)
            val prima = contenuto(spostata)
            val presenter = apriLista(sessione)

            presenter.azioni.togliDallaLista(progetto(presenter, "test"))
            advanceUntilIdle()

            assertEquals(emptyList(), dati(presenter).progetti)
            assertTrue(registro.elenco().isEmpty())
            assertEquals(prima, contenuto(spostata))
        }

    @Test
    fun `EX-12 riattaccato il disco, test torna una scheda normale che si apre e si elimina`() =
        conSessione(registro, dispatcher) { sessione ->
            registraTest(sessione)
            Files.move(test, spostata)
            assertFalse(progetto(apriLista(sessione), "test").cartellaTrovata)
            Files.move(spostata, test)

            val riaperta = apriLista(sessione)
            assertTrue(progetto(riaperta, "test").cartellaTrovata)

            riaperta.azioni.apri(progetto(riaperta, "test").percorso)
            advanceUntilIdle()
            assertEquals(test.toString(), assertNotNull(sessione.corrente.value).percorso)
            sessione.chiudi()

            val lista = apriLista(sessione)
            lista.azioni.chiediElimina(progetto(lista, "test"))
            lista.azioni.confermaElimina("test")
            advanceUntilIdle()
            assertFalse(Files.exists(test))
            assertEquals(emptyList(), dati(lista).progetti)
        }

    @Test
    fun `EX-13 aprire test non trovata avvisa Cartella non trovata e la lascia nella lista`() =
        conSessione(registro, dispatcher) { sessione ->
            registraTest(sessione)
            Files.move(test, spostata)
            val presenter = apriLista(sessione)

            presenter.azioni.apri(progetto(presenter, "test").percorso)
            advanceUntilIdle()

            assertEquals("Cartella non trovata: ~/Documents/snastro/test.snastro", dati(presenter).erroreApri)
            assertNull(sessione.corrente.value)
            assertEquals(listOf("test"), dati(presenter).progetti.map(ProgettoVista::nome))
            assertEquals(listOf(test.toString()), registro.elenco().map { it.percorso })
        }

    /** "test" created under `~/Documents/snastro`, closed, and in the registry. */
    private fun registraTest(sessione: SessioneProgettoImpl) {
        val creato = sessione.crea(documenti.toString(), "test").atteso()
        sessione.chiudi()
        attendiFinche(timeout = 10.seconds, messaggio = "test registrato") {
            registro.elenco().any { it.percorso == creato.percorso }
        }
    }

    /** Opens the list: a fresh presenter, its list loaded. */
    private fun TestScope.apriLista(sessione: SessioneProgettoImpl): ProgettiPresenter =
        ProgettiPresenter(scopePresenter, dispatcher, elencoProgetti(registro), sessione, casa.toString())
            .also { advanceUntilIdle() }

    private fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)

    private fun progetto(presenter: ProgettiPresenter, nome: String): ProgettoVista =
        dati(presenter).progetti.single { it.nome == nome }

    /** The relative paths of every file and folder under [cartella], recursively. */
    private fun contenuto(cartella: Path): Set<String> =
        Files.walk(cartella).use { percorsi -> percorsi.map { cartella.relativize(it).toString() }.toList().toSet() }
}
