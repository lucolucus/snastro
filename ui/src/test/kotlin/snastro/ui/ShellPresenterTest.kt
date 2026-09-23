package snastro.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.ui.testi.messaggioPer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

@OptIn(ExperimentalCoroutinesApi::class)
class ShellPresenterTest {
    // A scope of its own (sharing the test's scheduler, but NOT a child of `runTest`'s own job): the
    // presenter's `corrente`-collector coroutine is meant to live for the presenter's whole lifetime
    // (never completes on its own) — a child of `runTest`'s scope still running at the end would fail
    // with `UncompletedCoroutinesError`. `advanceUntilIdle()` still drains it (same `testScheduler`).
    private fun presentatore(
        scope: TestScope,
        sezioni: Set<DestinazioneShell> = OGNI_SEZIONE,
        sessione: SessioneProgetto = SessioneProgettoFinta(),
    ): ShellPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return ShellPresenter(CoroutineScope(dispatcher), dispatcher, sessione, sezioni)
    }

    @Test
    fun `AC-177 senza progetto lo stato iniziale e SenzaProgetto`() = runTest {
        val presenter = presentatore(this)
        assertEquals(ShellUiStato.SenzaProgetto, presenter.stato.value)
    }

    @Test
    fun `AC-177 dopo crea lo stato diventa ConProgetto con Registrazioni selezionata`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(DestinazioneShell.REGISTRAZIONI, stato.destinazioneSelezionata)
        assertEquals(OGNI_SEZIONE, stato.destinazioniDisponibili)
        assertEquals("Riunione", stato.progetto.nome)
    }

    @Test
    fun `AC-177 dopo chiudi lo stato torna SenzaProgetto`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()
        presenter.chiudi()
        advanceUntilIdle()
        assertEquals(ShellUiStato.SenzaProgetto, presenter.stato.value)
    }

    @Test
    fun `AC-181 un errore di apertura mostra il messaggio mappato e non apre nulla`() = runTest {
        val presenter = presentatore(this)
        presenter.apri("/percorso/inesistente")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.ErroreApertura>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), stato.messaggio)
    }

    @Test
    fun `AC-181 un nome vuoto mostra il messaggio di errore mappato`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.ErroreApertura>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.NomeProgettoVuoto), stato.messaggio)
    }

    @Test
    fun `AC-341 senza la sezione Parlanti la selezione iniziale e Registrazioni e seleziona Parlanti e ignorato`() =
        runTest {
            val presenter = presentatore(this, sezioni = setOf(DestinazioneShell.REGISTRAZIONI))
            presenter.crea("/tmp", "Riunione")
            advanceUntilIdle()
            val prima = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
            assertEquals(setOf(DestinazioneShell.REGISTRAZIONI), prima.destinazioniDisponibili)

            presenter.seleziona(DestinazioneShell.PARLANTI)

            val dopo = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
            assertEquals(DestinazioneShell.REGISTRAZIONI, dopo.destinazioneSelezionata)
        }

    @Test
    fun `AC-341 con la sezione Parlanti seleziona Parlanti cambia la destinazione`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()

        presenter.seleziona(DestinazioneShell.PARLANTI)

        val stato = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(DestinazioneShell.PARLANTI, stato.destinazioneSelezionata)
    }
}
