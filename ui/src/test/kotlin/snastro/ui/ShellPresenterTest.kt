package snastro.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

/**
 * fix-batch-16 MED-1(a): a [SessioneProgetto] whose `chiudi` blocks (like the real one waiting for a
 * running Elaborazione to stop) until [sblocca] opens — a hand-written fake by delegation (RC-9).
 */
private class SessioneProgettoCheChiudeLentamente(private val delegato: SessioneProgetto = SessioneProgettoFinta()) :
    SessioneProgetto by delegato {
    val chiudiIniziato = CountDownLatch(1)
    val sblocca = CountDownLatch(1)

    override fun chiudi() {
        chiudiIniziato.countDown()
        sblocca.await()
        delegato.chiudi()
    }
}

/**
 * L457b: a [SessioneProgetto] whose `crea` advances [corrente] to a NEWER Progetto than the one it
 * returns — before returning — simulating `corrente` having moved on (e.g. a concurrent reload)
 * between `sessione.crea` finishing and the presenter reading its result.
 */
private class SessioneProgettoConCorrenteChePassaAvanti(
    private val restituito: ProgettoAperto,
    private val piuNuovo: ProgettoAperto,
) : SessioneProgetto {
    private val _corrente = MutableStateFlow<ProgettoAperto?>(null)
    override val corrente: StateFlow<ProgettoAperto?> = _corrente

    override fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto> {
        _corrente.value = piuNuovo
        return Esito.Ok(restituito)
    }

    override fun apri(percorso: String): Esito<ProgettoAperto> = crea("", "")

    override fun chiudi() {
        _corrente.value = null
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
        // L457a: rethrown, not swallowed — never turned into an error banner — but (unlike before)
        // no longer stuck at Caricamento either: the `finally` resets it from `sessione.corrente`.
        assertEquals(ShellUiStato.SenzaProgetto(), presenter.stato.value)
    }

    @Test
    fun `L457a una CancellationException durante apri non lascia lo stato bloccato in Caricamento`() = runTest {
        val presenter =
            presentatore(this, sessione = SessioneProgettoCheEsplode { CancellationException("annullato") })
        presenter.apri("/tmp/qualsiasi")
        advanceUntilIdle()
        assertEquals(false, presenter.stato.value is ShellUiStato.Caricamento)
    }

    @Test
    fun `L457b un successo di crea mostra sessione corrente non il valore restituito se e piu nuovo`() = runTest {
        val restituito = ProgettoAperto(ProgettoId("id-vecchio"), "Vecchio", "/tmp/vecchio")
        val piuNuovo = ProgettoAperto(ProgettoId("id-nuovo"), "Nuovo", "/tmp/nuovo")
        val presenter = presentatore(
            this,
            sessione = SessioneProgettoConCorrenteChePassaAvanti(restituito, piuNuovo),
        )

        presenter.crea("/tmp", "Vecchio")
        advanceUntilIdle()

        val stato = assertIs<ShellUiStato.ConProgetto>(presenter.stato.value)
        assertEquals(piuNuovo, stato.progetto)
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

    // --- fix-batch-16 MED-1(a): chiudi never blocks the UI dispatcher, never sticks in Caricamento ---

    @Test
    fun `fix-batch-16 chiudi non blocca il dispatcher della UI, mostra Caricamento e poi torna a S1`() {
        val esecutoreUi = Executors.newSingleThreadExecutor()
        val esecutoreIo = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + esecutoreUi.asCoroutineDispatcher())
        val sessione = SessioneProgettoCheChiudeLentamente()
        try {
            val presenter = ShellPresenter(scope, esecutoreIo.asCoroutineDispatcher(), sessione, OGNI_SEZIONE)
            scope.launch { presenter.crea("/tmp", "Riunione") }
            attendiFinche { presenter.stato.value is ShellUiStato.ConProgetto }

            scope.launch { presenter.chiudi() } // from the UI thread, like the view's click
            assertTrue(sessione.chiudiIniziato.await(ATTESA_S, TimeUnit.SECONDS))

            // sessione.chiudi is still blocked: the UI thread must be free to run anything else (a timed
            // get, never a coroutine probe — that would itself wait on a blocked UI thread forever).
            val statoLettoDallaUi = esecutoreUi.submit(Callable { presenter.stato.value })
            assertEquals(ShellUiStato.Caricamento, statoLettoDallaUi.get(ATTESA_UI_MS, TimeUnit.MILLISECONDS))

            sessione.sblocca.countDown()
            attendiFinche { presenter.stato.value == ShellUiStato.SenzaProgetto() }
        } finally {
            sessione.sblocca.countDown()
            scope.cancel()
            esecutoreUi.shutdownNow()
            esecutoreIo.shutdownNow()
        }
    }

    @Test
    fun `fix-batch-16 chiudi senza un cambio di corrente non lascia lo stato bloccato in Caricamento`() = runTest {
        // Nothing open: `corrente` stays null, its collector never re-emits — only the finally frees the state.
        val presenter = presentatore(this)
        presenter.chiudi()
        advanceUntilIdle()
        assertEquals(ShellUiStato.SenzaProgetto(), presenter.stato.value)
    }

    @Test
    fun `fix-batch-16 un chiudi che lancia mostra l errore generico e non resta in Caricamento`() = runTest {
        val sessione = object : SessioneProgetto by SessioneProgettoFinta() {
            override fun chiudi() = error("boom")
        }
        val presenter = presentatore(this, sessione = sessione)
        presenter.chiudi()
        advanceUntilIdle()
        assertEquals(ShellUiStato.SenzaProgetto(erroreApertura = MESSAGGIO_ERRORE_GENERICO), presenter.stato.value)
    }

    private fun attendiFinche(condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + ATTESA_S * MS_PER_S
        while (!condizione()) {
            check(System.currentTimeMillis() < scadenza) { "timeout in attesa dello stato atteso" }
            Thread.sleep(PASSO_MS)
        }
    }

    private companion object {
        const val ATTESA_S = 5L
        const val ATTESA_UI_MS = 1_000L
        const val MS_PER_S = 1_000L
        const val PASSO_MS = 10L
    }
}
