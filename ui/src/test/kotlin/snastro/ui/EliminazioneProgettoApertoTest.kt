package snastro.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import snastro.kernel.Esito
import snastro.ui.progetti.ConfermaEliminazione
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

/** Like [SessioneProgettoFinta], but `elimina` answers [esito] and counts its calls. */
private class SessioneCheElimina(
    private val esito: () -> Esito<ProgettoEliminato>,
    private val delegato: SessioneProgetto = SessioneProgettoFinta(),
) : SessioneProgetto by delegato {
    var eliminati = 0
        private set

    override fun elimina(percorso: String): Esito<ProgettoEliminato> {
        eliminati++
        return esito()
    }
}

/** R0 (EX-4, EX-5): the shell's delete flow of the open project ([EliminazioneProgettoAperto]), over fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminazioneProgettoApertoTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @AfterEach
    fun chiudi() = scope.cancel()

    /** The shell over [sessione], "Riunione" created and open there. */
    private fun TestScope.conRiunioneAperta(sessione: SessioneProgetto = SessioneProgettoFinta()): ShellPresenter =
        ShellPresenter(scope, dispatcher, sessione, OGNI_SEZIONE).also {
            it.crea("/tmp", "Riunione")
            advanceUntilIdle()
        }

    private fun ShellPresenter.conProgetto(): ShellUiStato.ConProgetto = assertIs(stato.value)

    @Test
    fun `chiedi apre la conferma del progetto aperto e annulla la chiude`() = runTest(dispatcher) {
        val shell = conRiunioneAperta()

        shell.azioni.chiediElimina()
        assertEquals(ConfermaEliminazione("Riunione", "/tmp/Riunione.snastro"), shell.conProgetto().conferma)

        shell.azioni.annullaElimina()
        assertNull(shell.conProgetto().conferma)
    }

    @Test
    fun `EX-4 confermato col nome esatto elimina il progetto aperto e torna a S1`() = runTest(dispatcher) {
        val sessione = SessioneProgettoFinta()
        val shell = conRiunioneAperta(sessione)

        shell.azioni.chiediElimina()
        shell.azioni.confermaElimina("Riunione")
        advanceUntilIdle()

        assertEquals(ShellUiStato.SenzaProgetto(), shell.stato.value)
        assertNull(sessione.corrente.value)
    }

    @Test
    fun `un nome diverso non elimina niente`() = runTest(dispatcher) {
        val sessione = SessioneCheElimina({ Esito.Ok(ProgettoEliminato.Completamente) })
        val shell = conRiunioneAperta(sessione)
        shell.azioni.chiediElimina()

        shell.azioni.confermaElimina("riunione")
        advanceUntilIdle()

        assertEquals(0, sessione.eliminati)
        assertIs<ShellUiStato.ConProgetto>(shell.stato.value)
    }

    @Test
    fun `EX-5 un rifiuto resta nella conferma e il progetto resta aperto`() = runTest(dispatcher) {
        val sessione = SessioneCheElimina({ Esito.Errore(ErroreSessione.ElaborazioneInCorso) })
        val shell = conRiunioneAperta(sessione)

        shell.azioni.chiediElimina()
        shell.azioni.confermaElimina("Riunione")
        advanceUntilIdle()

        val stato = shell.conProgetto()
        assertEquals(messaggioPer(ErroreSessione.ElaborazioneInCorso), stato.conferma?.errore)
        assertEquals(false, stato.eliminazioneInCorso)
    }

    @Test
    fun `M1b un elimina che lancia mostra il messaggio generico nella conferma`() = runTest(dispatcher) {
        val shell = conRiunioneAperta(SessioneCheElimina({ error("boom") }))

        shell.azioni.chiediElimina()
        shell.azioni.confermaElimina("Riunione")
        advanceUntilIdle()

        assertEquals(MESSAGGIO_ERRORE_GENERICO, shell.conProgetto().conferma?.errore)
    }

    @Test
    fun `M3 mentre l eliminazione e in corso niente altro parte`() = runTest(dispatcher) {
        val sessione = SessioneCheElimina({ Esito.Errore(ErroreSessione.ElaborazioneInCorso) })
        val shell = conRiunioneAperta(sessione)
        shell.azioni.chiediElimina()

        shell.azioni.confermaElimina("Riunione")
        assertTrue(shell.conProgetto().eliminazioneInCorso)
        shell.azioni.confermaElimina("Riunione")
        shell.azioni.annullaElimina()
        shell.azioni.chiudi()
        advanceUntilIdle()

        assertEquals(1, sessione.eliminati)
        assertIs<ShellUiStato.ConProgetto>(shell.stato.value).also { assertEquals("Riunione", it.conferma?.nome) }
    }
}
