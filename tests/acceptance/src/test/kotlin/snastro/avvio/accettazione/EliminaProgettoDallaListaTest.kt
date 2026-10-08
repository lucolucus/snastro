package snastro.avvio.accettazione

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.ui.progetti.ProgettiPresenter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R0 · elimina progetto dalla lista (EX-1, EX-2, EX-3): the list's presenter over the REAL [SessioneProgettoImpl]
 * and real project folders — "skatefest", "mangu 2" and "test", none open.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. The presenter runs on [BancoS1]'s
 * dispatcher, shared with `runTest` and advanced explicitly, its scope cancelled in teardown; the session, which
 * registers a closed project on real threads, is built by [conSessione]: closed and its scope cancelled when the test
 * ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaProgettoDallaListaTest : BancoS1() {
    @Test
    fun `EX-1 Elimina su test, digitato test e confermato, cancella la cartella e la toglie dalla lista`() =
        conSessione(registro, dispatcher) { sessione ->
            val presenter = listaConTreProgetti(sessione)

            elimina(presenter, "test")

            assertFalse(Files.exists(documenti.resolve("test.snastro")))
            assertEquals(setOf("skatefest", "mangu 2"), nomi(presenter).toSet())
            assertNull(dati(presenter).conferma)
        }

    @Test
    fun `EX-2 un nome digitato diverso da test non conferma e non cancella niente`() =
        conSessione(registro, dispatcher) { sessione ->
            val presenter = listaConTreProgetti(sessione)
            presenter.azioni.chiediElimina(progetto(presenter, "test"))

            for (digitato in listOf("Test", "tes")) {
                assertFalse(assertNotNull(dati(presenter).conferma).confermataDa(digitato), digitato)
                presenter.azioni.confermaElimina(digitato)
                advanceUntilIdle()
            }

            assertNotNull(dati(presenter).conferma)
            assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
            assertEquals(setOf("skatefest", "mangu 2", "test"), nomi(presenter).toSet())
        }

    @Test
    fun `EX-3 Annulla dopo Elimina su test non cancella niente e lascia la lista invariata`() =
        conSessione(registro, dispatcher) { sessione ->
            val presenter = listaConTreProgetti(sessione)
            val prima = nomi(presenter)

            presenter.azioni.chiediElimina(progetto(presenter, "test"))
            presenter.azioni.annullaElimina()
            advanceUntilIdle()

            assertTrue(Files.isDirectory(documenti.resolve("test.snastro")))
            assertNull(dati(presenter).conferma)
            assertEquals(prima, nomi(presenter))
        }

    /** The three projects on disk and in the registry, then the list loaded by the presenter. */
    private fun TestScope.listaConTreProgetti(sessione: SessioneProgettoImpl): ProgettiPresenter {
        for (nome in listOf("skatefest", "mangu 2", "test")) creaChiuso(sessione, nome)
        return lista(sessione)
    }
}
