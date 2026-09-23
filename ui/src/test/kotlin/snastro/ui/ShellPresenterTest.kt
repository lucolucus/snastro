package snastro.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

/**
 * A [SessioneProgetto] whose `apri`/`crea` throw instead of returning [Esito] (M1(b)): a hand-written
 * fake, not a MockK stub, per RC-9/CR-17.
 */
private class SessioneProgettoCheEsplode(private val eccezione: () -> Throwable) : SessioneProgetto {
    override val corrente: StateFlow<ProgettoAperto?> = MutableStateFlow(null)

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> = throw eccezione()

    override fun apri(percorso: String): Esito<ProgettoAperto> = throw eccezione()

    override fun chiudi() = Unit
}

/** Counts `crea` calls (M3): wraps [SessioneProgettoFinta] by delegation rather than a MockK spy. */
private class SessioneProgettoContaChiamate(private val delegato: SessioneProgetto = SessioneProgettoFinta()) :
    SessioneProgetto by delegato {
    var chiamateCrea = 0
        private set

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        chiamateCrea++
        return delegato.crea(cartellaGenitore, nome)
    }
}

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
        assertEquals(ShellUiStato.SenzaProgetto(), presenter.stato.value)
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
        assertEquals(ShellUiStato.SenzaProgetto(), presenter.stato.value)
    }

    @Test
    fun `AC-181 un errore di apertura senza progetto mostra il messaggio mappato e non apre nulla`() = runTest {
        val presenter = presentatore(this)
        presenter.apri("/percorso/inesistente")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.SenzaProgetto>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), stato.erroreApertura)
    }

    @Test
    fun `AC-181 un nome vuoto mostra il messaggio di errore mappato`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.SenzaProgetto>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreSessione.NomeProgettoVuoto), stato.erroreApertura)
    }

    @Test
    fun `H1 un errore di apertura senza progetto lascia S1 raggiungibile e chiudiErrore lo rimuove`() = runTest {
        val presenter = presentatore(this)
        presenter.apri("/percorso/inesistente")
        advanceUntilIdle()
        assertIs<ShellUiStato.SenzaProgetto>(presenter.stato.value)

        presenter.chiudiErrore()

        assertEquals(ShellUiStato.SenzaProgetto(), presenter.stato.value)
    }

    @Test
    fun `H1 un errore di apertura con un progetto gia aperto mantiene ConProgetto e la nav`() = runTest {
        val presenter = presentatore(this)
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()
        val aperto = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)

        presenter.apri("/percorso/inesistente")
        advanceUntilIdle()

        val conErrore = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(aperto.progetto, conErrore.progetto)
        assertEquals(aperto.destinazioniDisponibili, conErrore.destinazioniDisponibili)
        assertEquals(messaggioPer(ErroreSessione.CartellaNonValida), conErrore.erroreApertura)

        presenter.chiudiErrore()

        val dopo = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(aperto.progetto, dopo.progetto)
        assertEquals(null, dopo.erroreApertura)
    }

    @Test
    fun `M1a riaprire lo stesso progetto gia aperto non lascia lo stato bloccato in Caricamento`() = runTest {
        val sessione = SessioneProgettoFinta()
        val presenter = presentatore(this, sessione = sessione)
        presenter.crea("/tmp", "Riunione")
        advanceUntilIdle()
        val aperto = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)

        // `sessione.corrente` is already this Progetto: `apri` re-sets an EQUAL value, so the
        // presenter's own collector alone would never re-emit and would leave `_stato` at Caricamento.
        presenter.apri(aperto.progetto.percorso)
        advanceUntilIdle()

        val stato = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(aperto.progetto, stato.progetto)
    }

    @Test
    fun `M1b una eccezione non di cancellazione durante apri mostra un errore generico`() = runTest {
        val presenter = presentatore(this, sessione = SessioneProgettoCheEsplode { IllegalStateException("boom") })
        presenter.apri("/tmp/qualsiasi")
        advanceUntilIdle()
        val stato = assertIs<ShellUiStato.SenzaProgetto>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, stato.erroreApertura)
    }

    @Test
    fun `M1b una CancellationException durante apri non diventa un errore generico`() = runTest {
        val presenter =
            presentatore(this, sessione = SessioneProgettoCheEsplode { CancellationException("annullato") })
        presenter.apri("/tmp/qualsiasi")
        advanceUntilIdle()
        // Rethrown, not swallowed: the operation stays cancelled, it is never turned into a banner.
        assertEquals(ShellUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `M3 due richieste crea ravvicinate eseguono sessione crea una sola volta`() = runTest {
        val sessione = SessioneProgettoContaChiamate()
        val presenter = presentatore(this, sessione = sessione)

        presenter.crea("/tmp", "Uno")
        presenter.crea("/tmp", "Due")
        advanceUntilIdle()

        assertEquals(1, sessione.chiamateCrea)
        val stato = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals("Uno", stato.progetto.nome)
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
