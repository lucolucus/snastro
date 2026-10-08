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
import snastro.ui.progetti.ProgettiPresenter
import snastro.ui.progetti.ProgettiUiStato
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 · elimina progetto dalla lista (EX-1, EX-2, EX-3): the list's presenter over the REAL [SessioneProgettoImpl]
 * and real project folders — "skatefest", "mangu 2" and "test", none open.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * ML/audio ([componentiDiProva]), id generator and clock. The presenter runs on a [StandardTestDispatcher]
 * advanced explicitly; the session keeps its own real scope. Both scopes are cancelled in teardown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaProgettoDallaListaTest {
    @TempDir
    lateinit var documenti: Path

    private val registro = RegistroProgettiFinta()
    private val dispatcher = StandardTestDispatcher()
    private val scopePresenter = CoroutineScope(SupervisorJob() + dispatcher)
    private val scopeSessione = CoroutineScope(SupervisorJob())
    private val sessione = SessioneProgettoImpl(
        registro,
        GeneratoreIdFinto(),
        Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC),
        scopeSessione,
        componentiDiProva(),
    )

    @AfterEach
    fun chiudi() {
        sessione.chiudi()
        scopePresenter.cancel()
        scopeSessione.cancel()
    }

    @Test
    fun `EX-1 Elimina su test, digitato test e confermato, cancella la cartella e la toglie dalla lista`() =
        runTest(dispatcher) {
            val presenter = listaConTreProgetti()

            presenter.chiediElimina(progetto(presenter, "test"))
            presenter.confermaElimina("test")
            advanceUntilIdle()

            assertFalse(Files.exists(documenti.resolve("test.snastro")))
            assertEquals(setOf("skatefest", "mangu 2"), nomi(presenter).toSet())
            assertNull(dati(presenter).conferma)
        }

    @Test
    fun `EX-2 un nome digitato diverso da test non conferma e non cancella niente`() = runTest(dispatcher) {
        val presenter = listaConTreProgetti()
        presenter.chiediElimina(progetto(presenter, "test"))

        for (digitato in listOf("Test", "tes")) {
            assertFalse(assertNotNull(dati(presenter).conferma).confermataDa(digitato), digitato)
            presenter.confermaElimina(digitato)
            advanceUntilIdle()
        }

        assertNotNull(dati(presenter).conferma)
        assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
        assertEquals(setOf("skatefest", "mangu 2", "test"), nomi(presenter).toSet())
    }

    @Test
    fun `EX-3 Annulla dopo Elimina su test non cancella niente e lascia la lista invariata`() = runTest(dispatcher) {
        val presenter = listaConTreProgetti()
        val prima = nomi(presenter)

        presenter.chiediElimina(progetto(presenter, "test"))
        presenter.annullaElimina()
        advanceUntilIdle()

        assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
        assertNull(dati(presenter).conferma)
        assertEquals(prima, nomi(presenter))
    }

    /** The three projects on disk and in the registry, then the list loaded by the presenter. */
    private fun TestScope.listaConTreProgetti(): ProgettiPresenter {
        for (nome in listOf("skatefest", "mangu 2", "test")) {
            val creato = sessione.crea(documenti.toString(), nome).atteso()
            sessione.chiudi()
            attendiFinche(timeout = 10.seconds, messaggio = "$nome registrato") {
                registro.elenco().any { it.percorso == creato.percorso }
            }
        }
        return ProgettiPresenter(scopePresenter, dispatcher, ElencoProgetti(registro), sessione, cartellaUtente = "")
            .also { advanceUntilIdle() }
    }

    private fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)

    private fun nomi(presenter: ProgettiPresenter): List<String> = dati(presenter).progetti.map(ProgettoVista::nome)

    private fun progetto(presenter: ProgettiPresenter, nome: String): ProgettoVista =
        dati(presenter).progetti.single { it.nome == nome }
}
