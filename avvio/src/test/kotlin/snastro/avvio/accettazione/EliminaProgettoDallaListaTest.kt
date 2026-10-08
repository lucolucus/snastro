package snastro.avvio.accettazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * R0 · elimina progetto dalla lista (EX-1, EX-2, EX-3): the list's state holder over the REAL session, real project
 * folders and one shared registry — "skatefest", "mangu 2" and "test", none open.
 */
class EliminaProgettoDallaListaTest {
    @TempDir
    lateinit var documenti: Path

    private val registro = RegistroProgettiFinta()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val sessione = SessioneProgettoImpl(
        registro,
        GeneratoreIdFinto(),
        Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC),
        scope,
        componentiDiProva(),
    )

    @AfterEach
    fun chiudi() {
        sessione.chiudi()
    }

    @Test
    fun `EX-1 Elimina su test, digitato test e confermato, cancella la cartella e la toglie dalla lista`() {
        val presenter = listaConTreProgetti()

        presenter.chiediElimina(progetto(presenter, "test"))
        presenter.digitaNomeElimina("test")
        presenter.confermaElimina()

        assertFalse(Files.exists(documenti.resolve("test.snastro")))
        assertEquals(setOf("skatefest", "mangu 2"), nomi(presenter).toSet())
        assertNull(dati(presenter).conferma)
    }

    @Test
    fun `EX-2 un nome digitato diverso da test lascia la conferma disattiva e non cancella niente`() {
        val presenter = listaConTreProgetti()
        presenter.chiediElimina(progetto(presenter, "test"))

        for (digitato in listOf("Test", "tes")) {
            presenter.digitaNomeElimina(digitato)
            assertFalse(assertNotNull(dati(presenter).conferma).confermabile, digitato)
            presenter.confermaElimina()
        }

        assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
        assertEquals(setOf("skatefest", "mangu 2", "test"), nomi(presenter).toSet())
    }

    @Test
    fun `EX-3 Annulla dopo Elimina su test non cancella niente e lascia la lista invariata`() {
        val presenter = listaConTreProgetti()
        val prima = nomi(presenter)

        presenter.chiediElimina(progetto(presenter, "test"))
        presenter.digitaNomeElimina("test")
        presenter.annullaElimina()

        assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
        assertNull(dati(presenter).conferma)
        assertEquals(prima, nomi(presenter))
    }

    private fun listaConTreProgetti(): ProgettiPresenter {
        for (nome in listOf("skatefest", "mangu 2", "test")) {
            val creato = sessione.crea(documenti.toString(), nome).atteso()
            sessione.chiudi()
            attendiFinche(timeout = 10.seconds, messaggio = "$nome registrato") {
                registro.elenco().any { it.percorso == creato.percorso }
            }
        }
        return ProgettiPresenter(scope, Dispatchers.Unconfined, ElencoProgetti(registro), sessione)
    }

    private fun dati(presenter: ProgettiPresenter): ProgettiUiStato.Dati = assertIs(presenter.stato.value)

    private fun nomi(presenter: ProgettiPresenter): List<String> = dati(presenter).progetti.map(ProgettoVista::nome)

    private fun progetto(presenter: ProgettiPresenter, nome: String): ProgettoVista =
        dati(presenter).progetti.single { it.nome == nome }
}
