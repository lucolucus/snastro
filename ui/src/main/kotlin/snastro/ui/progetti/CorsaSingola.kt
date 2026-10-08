package snastro.ui.progetti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * The one single-flight runner of S1 · Progetti over its shared [stato], used by [ProgettiPresenter] and
 * [FlussoEliminazione] alike: one copy of the M3 guard (a second operation while one is in flight is ignored) and of
 * M1(b) (a non-cancellation fault becomes the one fixed generic message).
 */
internal class CorsaSingola(
    private val stato: MutableStateFlow<ProgettiUiStato>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    /** The loaded state, or null while the list is still loading. */
    val dati: ProgettiUiStato.Dati? get() = stato.value as? ProgettiUiStato.Dati

    /** Applies [f] to the [ProgettiUiStato.Dati] state; a no-op while the list is still loading. */
    fun aggiorna(f: (ProgettiUiStato.Dati) -> ProgettiUiStato.Dati) {
        val attuale = stato.value
        if (attuale is ProgettiUiStato.Dati) stato.value = f(attuale)
    }

    // M3: guards a double crea/apri/elimina while one is already in flight. `inCorso` is set synchronously
    // here, before `scope.launch` — unlike ShellPresenter (where `_stato` becomes Caricamento only
    // inside the launched body, needing an extra Job flag), a second synchronous call already reads
    // the updated `stato.value` and returns immediately: no separate Job guard is needed.
    fun <T> avvia(
        pulisci: (ProgettiUiStato.Dati) -> ProgettiUiStato.Dati,
        operazione: suspend () -> Esito<T>,
        alSuccesso: (ProgettiUiStato.Dati, T) -> ProgettiUiStato.Dati,
        alFallimento: (ProgettiUiStato.Dati, String) -> ProgettiUiStato.Dati,
    ) {
        val attuale = stato.value
        if (attuale !is ProgettiUiStato.Dati || attuale.inCorso) return
        stato.value = pulisci(attuale).copy(inCorso = true)
        scope.launch {
            try {
                when (val esito = withContext(io) { operazione() }) {
                    is Esito.Ok -> aggiorna { alSuccesso(it, esito.valore) }
                    is Esito.Errore -> aggiorna { alFallimento(it, messaggioPer(esito.errore)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Any other fault of `sessione.apri`/`crea`/`elimina` becomes the one FIXED, plain-Italian
                // message the user sees (never a stack trace) — same rationale as ShellPresenter M1(b).
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiorna { alFallimento(it, MESSAGGIO_ERRORE_GENERICO) }
            } finally {
                // fix-batch-12 #5: reset LAST and UNCONDITIONALLY — Error and Cancellation alike — so
                // a cancelled operation (e.g. a spurious CancellationException from the port, same
                // family as LettorePresenter's own fix) never leaves `inCorso` stuck true, which would
                // silently block every later one behind the M3 guard forever.
                aggiorna { it.copy(inCorso = false) }
            }
        }
    }
}
