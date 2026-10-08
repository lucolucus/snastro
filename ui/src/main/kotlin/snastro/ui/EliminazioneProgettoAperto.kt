package snastro.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.supporto.catturaNonFatale
import snastro.ui.progetti.ConfermaEliminazione
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * The delete flow of the open project (R0, EX-4/EX-5), asked from the shell's header, over the shell's own [stato]:
 * the confirmation, then [SessioneProgetto.elimina] — which closes the project before deleting it, or refuses while
 * something runs or waits in its queue — and S1 once it is gone. A failure stays in the confirmation. [occupata] is
 * the shell's own M3 guard: nothing is asked, cancelled or started while a shell operation is in flight.
 */
internal class EliminazioneProgettoAperto(
    private val stato: MutableStateFlow<ShellUiStato>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val sessione: SessioneProgetto,
    private val occupata: () -> Boolean,
) {
    /** EX-4: opens the delete confirmation of the open project. */
    fun chiedi() {
        if (!occupata()) aggiorna { it.copy(conferma = ConfermaEliminazione(it.progetto.nome, it.progetto.percorso)) }
    }

    /** Closes the confirmation, unless its delete is already running; nothing is deleted. */
    fun annulla() {
        if (!occupata()) aggiorna { it.copy(conferma = null) }
    }

    /**
     * EX-4: deletes the open project when [nomeDigitato] is its exact name (EX-2's rule: the view's disabled button
     * is not the only guard), then shows S1; EX-5: a refusal leaves it open, its message in the confirmation.
     */
    fun conferma(nomeDigitato: String) {
        val attuale = (stato.value as? ShellUiStato.ConProgetto)?.takeUnless { occupata() }
        val conferma = attuale?.conferma?.takeIf { it.confermataDa(nomeDigitato) }
        if (attuale == null || conferma == null) return
        stato.value = attuale.copy(conferma = conferma.copy(errore = null), eliminazioneInCorso = true)
        scope.launch {
            try {
                // M1(b): a non-fatal fault becomes the one fixed message; `catturaNonFatale` rethrows cancellation.
                val esito = catturaNonFatale { withContext(io) { sessione.elimina(conferma.percorso) } }.getOrNull()
                when (esito) {
                    // M1(a): S1 is set from the result, never left to the `corrente` collector alone.
                    is Esito.Ok -> stato.value = ShellUiStato.SenzaProgetto()
                    is Esito.Errore -> fallita(messaggioPer(esito.errore))
                    null -> fallita(MESSAGGIO_ERRORE_GENERICO)
                }
            } finally {
                // Reset unconditionally, cancellation included: a stuck flag would block every later shell operation.
                aggiorna { it.copy(eliminazioneInCorso = false) }
            }
        }
    }

    private fun fallita(messaggio: String) = aggiorna { it.copy(conferma = it.conferma?.copy(errore = messaggio)) }

    /** Applies [f] to the [ShellUiStato.ConProgetto] state; a no-op on any other. */
    private fun aggiorna(f: (ShellUiStato.ConProgetto) -> ShellUiStato.ConProgetto) {
        val attuale = stato.value
        if (attuale is ShellUiStato.ConProgetto) stato.value = f(attuale)
    }
}
