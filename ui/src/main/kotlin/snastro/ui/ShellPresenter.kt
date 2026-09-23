package snastro.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
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

    init {
        scope.launch {
            sessione.corrente.collect { progetto -> _stato.value = statoDi(progetto) }
        }
    }

    private fun statoDi(progetto: ProgettoAperto?): ShellUiStato =
        if (progetto != null) {
            ShellUiStato.ConProgetto(progetto, sezioniDisponibili, destinazioneCorrente)
        } else {
            ShellUiStato.SenzaProgetto
        }

    fun apri(percorso: String) {
        scope.launch {
            _stato.value = ShellUiStato.Caricamento
            segnalaSeErrore(withContext(io) { sessione.apri(percorso) })
        }
    }

    fun crea(cartellaGenitore: String, nome: String) {
        scope.launch {
            _stato.value = ShellUiStato.Caricamento
            segnalaSeErrore(withContext(io) { sessione.crea(cartellaGenitore, nome) })
        }
    }

    // On Esito.Ok the collector above moves the state on: `sessione.corrente`'s own emission is the
    // single source of truth for ConProgetto/SenzaProgetto (RC-1: the presenter re-decides nothing).
    private fun segnalaSeErrore(esito: Esito<ProgettoAperto>) {
        if (esito is Esito.Errore) {
            _stato.value = ShellUiStato.ErroreApertura(messaggioPer(esito.errore))
        }
    }

    fun chiudi() {
        sessione.chiudi()
    }

    fun seleziona(destinazione: DestinazioneShell) {
        if (destinazione !in sezioniDisponibili) return
        destinazioneCorrente = destinazione
        val attuale = _stato.value
        if (attuale is ShellUiStato.ConProgetto) {
            _stato.value = attuale.copy(destinazioneSelezionata = destinazione)
        }
    }

    val azioni: AzioniShell = AzioniShell(apri = ::apri, crea = ::crea, chiudi = ::chiudi, seleziona = ::seleziona)
}
