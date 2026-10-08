package snastro.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * State holder of the app shell (RC-2, thin UI): reflects [SessioneProgetto.corrente] and forwards
 * `crea`/`apri`/`chiudi`/`seleziona` to it; the open project's delete is [EliminazioneProgettoAperto]'s.
 * [sezioniDisponibili] is injected by the composition root (AC-341) — the single composition's `SEZIONI_SHELL` always
 * includes every [DestinazioneShell]; [cartellaUtente], the user's home, is written `~` in the folders the shell shows.
 */
class ShellPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val sessione: SessioneProgetto,
    private val sezioniDisponibili: Set<DestinazioneShell>,
    private val cartellaUtente: String,
) {
    init {
        require(sezioniDisponibili.isNotEmpty()) { "la shell richiede almeno una sezione disponibile" }
    }

    private var destinazioneCorrente: DestinazioneShell =
        DestinazioneShell.entries.first { it in sezioniDisponibili }

    private val _stato = MutableStateFlow(statoDi(sessione.corrente.value))
    val stato: StateFlow<ShellUiStato> = _stato.asStateFlow()

    // M3: guards a double apri/crea/chiudi while one is already in flight. A freshly launched Job is
    // `isActive` from the moment `launch` returns, even before its body starts running, so this also
    // catches two synchronous calls issued before the dispatcher gets a chance to run either.
    private var operazioneInCorso: Job? = null

    private val eliminazione = EliminazioneProgettoAperto(_stato, scope, io, sessione, ::occupata, cartellaUtente)

    init {
        scope.launch {
            // S1 already shown keeps its banner: the delete of the open project may set it before this sees `null`.
            sessione.corrente.collect { progetto ->
                _stato.update { attuale ->
                    if (progetto == null && attuale is ShellUiStato.SenzaProgetto) attuale else statoDi(progetto)
                }
            }
        }
    }

    /** M3: a shell operation — apri/crea/chiudi or the open project's delete — is in flight. */
    private fun occupata(): Boolean {
        val attuale = _stato.value
        return attuale is ShellUiStato.Caricamento || operazioneInCorso?.isActive == true ||
            (attuale is ShellUiStato.ConProgetto && attuale.eliminazioneInCorso)
    }

    private fun statoDi(progetto: ProgettoAperto?): ShellUiStato =
        if (progetto != null) {
            ShellUiStato.ConProgetto(progetto, sezioniDisponibili, destinazioneCorrente)
        } else {
            ShellUiStato.SenzaProgetto()
        }

    fun apri(percorso: String) = corri("apri", { sessione.apri(percorso) }, ::applica)

    fun crea(cartellaGenitore: String, nome: String) =
        corri("crea", { sessione.crea(cartellaGenitore, nome) }, ::applica)

    /**
     * fix-batch-16 MED-1(a): `sessione.chiudi` blocks (bounded) while the project's background work stops — never on
     * the UI thread. The collector normally already moved the state to S1; AC-347 says it never throws.
     */
    fun chiudi() = corri("chiudi", { sessione.chiudi() })

    /**
     * The one single-flight run of the shell's apri/crea/chiudi: ignored while a shell operation is in flight (M3);
     * [ShellUiStato.Caricamento] while [operazione] runs on [io]; its result handed to [applica]. A non-fatal fault,
     * logged with its cause under [cosa] (CR-7), becomes M1(b)'s one fixed generic message; cancellation propagates.
     */
    private fun <T : Any> corri(cosa: String, operazione: suspend () -> T, applica: (T) -> Unit = {}) {
        if (occupata()) return
        operazioneInCorso = scope.launch {
            _stato.value = ShellUiStato.Caricamento
            try {
                val risultato = nonFataleRegistrato(cosa) { withContext(io) { operazione() } }
                if (risultato != null) applica(risultato) else _stato.value = statoConErrore(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                // L457a: a cancellation never reaches `applica` or the generic message; without this `_stato` would
                // stay stuck at Caricamento for a `scope` that outlives this one cancelled operation.
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
            is Esito.Errore -> statoConErrore(messaggioPer(esito.errore, cartellaUtente))
        }
    }

    // H1: reads `sessione.corrente` — not `_stato.value`, which is `Caricamento` while an operation runs
    // — to decide whether the failure leaves a Progetto open: the banner overlays ConProgetto's nav when
    // one was already open, S1 otherwise. Either way it overlays the state, it never replaces it.
    private fun statoConErrore(messaggio: String): ShellUiStato {
        val progetto = sessione.corrente.value
        return if (progetto != null) {
            ShellUiStato.ConProgetto(progetto, sezioniDisponibili, destinazioneCorrente, avviso = messaggio)
        } else {
            ShellUiStato.SenzaProgetto(avviso = messaggio)
        }
    }

    /** H1: dismisses the current state's `avviso` banner, if any. */
    fun chiudiErrore() {
        _stato.value = when (val attuale = _stato.value) {
            is ShellUiStato.SenzaProgetto -> attuale.copy(avviso = null)
            is ShellUiStato.ConProgetto -> attuale.copy(avviso = null)
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
        chiediElimina = eliminazione::chiedi,
        annullaElimina = eliminazione::annulla,
        confermaElimina = eliminazione::conferma,
    )
}
