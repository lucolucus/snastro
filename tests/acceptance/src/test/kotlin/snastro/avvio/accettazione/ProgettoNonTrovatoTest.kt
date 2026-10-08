package snastro.avvio.accettazione

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.avvio.progetto.elencoProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R0 · progetto non trovato (EX-7, EX-8, EX-12, EX-13): the list's presenter over the REAL [SessioneProgettoImpl], the
 * real folder check of the list ([elencoProgetti]) and a real project folder `~/Documents/snastro/test.snastro`, moved
 * away to `~/Desktop/` and back — the user's home being a temporary folder.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta], one shared by the session and the list),
 * the user's home, ML/audio ([componentiDiProva]), id generator and clock. "Opening the list" is a fresh presenter, as
 * S1 is each time it is shown. The presenters run on [BancoS1]'s dispatcher, shared with `runTest` and advanced
 * explicitly, their scope cancelled in teardown; the session is built by [conSessione].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgettoNonTrovatoTest : BancoS1() {
    private val test: Path by lazy { documenti.resolve("test.snastro") }
    private val spostata: Path by lazy { Files.createDirectories(casa.resolve("Desktop")).resolve("test.snastro") }

    @Test
    fun `EX-7 test spostata sulla Scrivania appare nella lista come Cartella non trovata`() =
        conSessione(registro, dispatcher) { sessione ->
            creaChiuso(sessione, "test")
            Files.move(test, spostata)

            val presenter = lista(sessione)

            assertFalse(progetto(presenter, "test").cartellaTrovata)
        }

    @Test
    fun `EX-8 Togli dalla lista su test la toglie dalla lista e lascia intatta la cartella sulla Scrivania`() =
        conSessione(registro, dispatcher) { sessione ->
            creaChiuso(sessione, "test")
            Files.move(test, spostata)
            val prima = contenuto(spostata)
            val presenter = lista(sessione)

            presenter.azioni.togliDallaLista(progetto(presenter, "test"))
            advanceUntilIdle()

            assertEquals(emptyList(), dati(presenter).progetti)
            assertTrue(registro.elenco().isEmpty())
            assertEquals(prima, contenuto(spostata))
        }

    @Test
    fun `EX-12 riattaccato il disco, test torna una scheda normale che si apre e si elimina`() =
        conSessione(registro, dispatcher) { sessione ->
            creaChiuso(sessione, "test")
            Files.move(test, spostata)
            assertFalse(progetto(lista(sessione), "test").cartellaTrovata)
            Files.move(spostata, test)

            val riaperta = lista(sessione)
            assertTrue(progetto(riaperta, "test").cartellaTrovata)

            riaperta.azioni.apri(progetto(riaperta, "test").percorso)
            advanceUntilIdle()
            assertEquals(test.toString(), assertNotNull(sessione.corrente.value).percorso)
            sessione.chiudi()

            val chiusa = lista(sessione)
            elimina(chiusa, "test")
            assertFalse(Files.exists(test))
            assertEquals(emptyList(), dati(chiusa).progetti)
        }

    @Test
    fun `EX-13 aprire test non trovata avvisa Cartella non trovata e la lascia nella lista`() =
        conSessione(registro, dispatcher) { sessione ->
            creaChiuso(sessione, "test")
            Files.move(test, spostata)
            val presenter = lista(sessione)

            presenter.azioni.apri(progetto(presenter, "test").percorso)
            advanceUntilIdle()

            assertEquals("Cartella non trovata: ~/Documents/snastro/test.snastro", dati(presenter).erroreApri)
            assertNull(sessione.corrente.value)
            assertEquals(listOf("test"), nomi(presenter))
            assertEquals(listOf(test.toString()), registro.elenco().map { it.percorso })
        }
}
