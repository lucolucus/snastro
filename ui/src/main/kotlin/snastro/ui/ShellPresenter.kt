package snastro.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * State holder of the app shell (RC-2, thin UI): reflects [SessioneProgetto.corrente] and forwards
 * `crea`/`apri`/`chiudi`/`seleziona` to it. [sezioniDisponibili] is injected by the composition root
 * (AC-341) — R0/R1's `:avvio` wiring omits [DestinazioneShell.PARLANTI].
 */
class ShellPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val sessione: SessioneProgetto,
    private val sezioniDisponibili: Set<DestinazioneShell>,
) {
    init {
        require(sezioniDisponibili.isNotEmpty()) { "la shell richiede almeno una sezione disponibile" }
    }

    private var destinazioneCorrente: DestinazioneShell =
        DestinazioneShell.entries.first { it in sezioniDisponibili }

    private val _stato = MutableStateFlow(statoDi(sessione.corrente.value))
    val stato: StateFlow<ShellUiStato> = _stato.asStateFlow()

    // M3: guards a double apri/crea while one is already in flight. A freshly launched Job is
    // `isActive` from the moment `launch` returns, even before its body starts running, so this also
    // catches two synchronous calls issued before the dispatcher gets a chance to run either.
    private var operazioneInCorso: Job? = null

    init {
        scope.launch {
            sessione.corrente.collect { progetto -> _stato.value = statoDi(progetto) }
        }
    }

    private fun statoDi(progetto: ProgettoAperto?): ShellUiStato =
        if (progetto != null) {
            ShellUiStato.ConProgetto(progetto, sezioniDisponibili, destinazioneCorrente)
        } else {
            ShellUiStato.SenzaProgetto()
        }

    fun apri(percorso: String) = avvia { sessione.apri(percorso) }

    fun crea(cartellaGenitore: String, nome: String) = avvia { sessione.crea(cartellaGenitore, nome) }

    private fun avvia(operazione: suspend () -> Esito<ProgettoAperto>) {
        if (_stato.value is ShellUiStato.Caricamento || operazioneInCorso?.isActive == true) return
        operazioneInCorso = scope.launch {
            _stato.value = ShellUiStato.Caricamento
            try {
                applica(withContext(io) { operazione() })
            } catch (e: CancellationException) {
                throw e
            } catch (
                // M1(b): any other fault of `sessione.apri`/`crea` becomes the one FIXED, plain-Italian
                // message the user sees (never a stack trace) — this codebase has no logging sink to
                // hand the technical cause to (same rationale as `EseguiProssimaElaborazioneServizio`).
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _stato.value = statoConErrore(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                // L457a: same fallback as `chiudi` (:121) — a CancellationException above is rethrown
                // without ever reaching `applica`/the catch block, which would otherwise leave `_stato`
                // stuck at Caricamento forever for a `scope` that outlives this one cancelled operation.
                if (_stato.value is ShellUiStato.Caricamento) _stato.value = statoDi(sessione.corrente.value)
            }
        }
    }

    // M1(a): on Esito.Ok the state is set directly from the returned Progetto, never left to the
    // collector alone — a StateFlow does not re-emit a value equal to its current one (e.g. re-`apri`ing
    // the Progetto that is already `sessione.corrente`), which would leave `_stato` stuck at Caricamento
    // forever if this were the only path out of it. The collector above stays the source of truth for
    // `chiudi` and any change to `sessione.corrente` from outside this call (RC-1: re-decides nothing).
    private fun applica(esito: Esito<ProgettoAperto>) {
        _stato.value = when (esito) {
            // L457b: `sessione.corrente.value`, not `esito.valore` — if `corrente` already moved past
            // what THIS call returned (a newer apri/crea/reload landed there meanwhile), showing
            // `esito.valore` would overwrite that newer Progetto with a stale one. Falls back to
            // `esito.valore` only in the impossible case `corrente` reads back `null` right after a
            // successful apri/crea (CR-6: never `!!`).
            is Esito.Ok -> ShellUiStato.ConProgetto(
                sessione.corrente.value ?: esito.valore,
                sezioniDisponibili,
                destinazioneCorrente,
            )
            is Esito.Errore -> statoConErrore(messaggioPer(esito.errore))
        }
    }

    // H1: reads `sessione.corrente` — not `_stato.value`, which is `Caricamento` while an operation runs
    // — to decide whether the failure leaves a Progetto open: the banner overlays ConProgetto's nav when
    // one was already open, S1 otherwise. Either way it overlays the state, it never replaces it.
    private fun statoConErrore(messaggio: String): ShellUiStato {
        val progetto = sessione.corrente.value
        return if (progetto != null) {
            ShellUiStato.ConProgetto(progetto, sezioniDisponibili, destinazioneCorrente, erroreApertura = messaggio)
        } else {
            ShellUiStato.SenzaProgetto(erroreApertura = messaggio)
        }
    }

    /**
     * fix-batch-16 MED-1(a): `sessione.chiudi` blocks (bounded) while the project's background work stops
     * — never on the UI thread: it runs on [io], [ShellUiStato.Caricamento] shown meanwhile, and the
     * `finally` never leaves the state stuck there (the collector normally already moved it to S1).
     * Ignored while another apri/crea/chiudi is in flight (M3).
     */
    fun chiudi() {
        if (_stato.value is ShellUiStato.Caricamento || operazioneInCorso?.isActive == true) return
        operazioneInCorso = scope.launch {
            _stato.value = ShellUiStato.Caricamento
            try {
                withContext(io) { sessione.chiudi() }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // AC-347 says chiudi never throws; if it ever does, the same fixed message as M1(b).
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _stato.value = statoConErrore(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                if (_stato.value is ShellUiStato.Caricamento) _stato.value = statoDi(sessione.corrente.value)
            }
        }
    }

    /** H1: dismisses the current state's `erroreApertura` banner, if any. */
    fun chiudiErrore() {
        _stato.value = when (val attuale = _stato.value) {
            is ShellUiStato.SenzaProgetto -> attuale.copy(erroreApertura = null)
            is ShellUiStato.ConProgetto -> attuale.copy(erroreApertura = null)
            ShellUiStato.Caricamento -> attuale
        }
    }

    fun seleziona(destinazione: DestinazioneShell) {
        if (destinazione !in sezioniDisponibili) return
        destinazioneCorrente = destinazione
        val attuale = _stato.value
        if (attuale is ShellUiStato.ConProgetto) {
            _stato.value = attuale.copy(destinazioneSelezionata = destinazione)
        }
    }

    val azioni: AzioniShell = AzioniShell(
        apri = ::apri,
        crea = ::crea,
        chiudi = ::chiudi,
        chiudiErrore = ::chiudiErrore,
        seleziona = ::seleziona,
    )
}
