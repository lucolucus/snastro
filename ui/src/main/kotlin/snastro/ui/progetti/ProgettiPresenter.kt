package snastro.ui.progetti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.ui.ProgettoAperto
import snastro.ui.SessioneProgetto
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * State holder of S1 · Progetti (RC-2, thin UI): loads [ElencoProgetti] once and forwards
 * `crea`/`apri` to [SessioneProgetto]. Reapplies the shell's lessons (dev-architecture `#presenter`):
 * an error is a dismissible inline message over the list, never a replacement of it (H1); success is
 * applied from the returned [ProgettoAperto], never left to a collector alone (M1(a) — this presenter
 * has none, but the same "set state from the result" rule keeps `inCorso` from ever getting stuck);
 * a non-cancellation fault becomes the one fixed generic message (M1(b)); a second `crea`/`apri` while
 * one is already in flight is ignored (M3).
 */
class ProgettiPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val elenco: ElencoProgetti,
    private val sessione: SessioneProgetto,
) {
    private val _stato = MutableStateFlow<ProgettiUiStato>(ProgettiUiStato.Caricamento)
    val stato: StateFlow<ProgettiUiStato> = _stato.asStateFlow()

    init {
        scope.launch {
            try {
                val progetti = withContext(io) { elenco.progetti() }
                _stato.value = ProgettiUiStato.Dati(progetti)
            } catch (e: CancellationException) {
                throw e
            } catch (
                // fix-batch-12 #5: the initial load itself was unguarded — a throwing `elenco.progetti()`
                // left `_stato` stuck on Caricamento forever, with crea/apri unreachable (M5's own
                // rationale for RegistrazioniPresenter, applied here). Lands on the SAME Dati state a
                // dismissed erroreCrea would, actions usable right away.
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                _stato.value = ProgettiUiStato.Dati(progetti = emptyList(), erroreCrea = MESSAGGIO_ERRORE_GENERICO)
            }
        }
    }

    fun crea(cartellaGenitore: String, nome: String) = avvia(
        pulisci = { it.copy(erroreCrea = null) },
        operazione = { sessione.crea(cartellaGenitore, nome) },
        alFallimento = { stato, messaggio -> stato.copy(erroreCrea = messaggio) },
    )

    fun apri(percorso: String) = avvia(
        pulisci = { it.copy(erroreApri = null) },
        operazione = { sessione.apri(percorso) },
        alFallimento = { stato, messaggio -> stato.copy(erroreApri = messaggio) },
    )

    // M3: guards a double crea/apri while one is already in flight. `inCorso` is set synchronously
    // here, before `scope.launch` — unlike ShellPresenter (where `_stato` becomes Caricamento only
    // inside the launched body, needing an extra Job flag), a second synchronous call already reads
    // the updated `_stato.value` and returns immediately: no separate Job guard is needed.
    private fun avvia(
        pulisci: (ProgettiUiStato.Dati) -> ProgettiUiStato.Dati,
        operazione: suspend () -> Esito<ProgettoAperto>,
        alFallimento: (ProgettiUiStato.Dati, String) -> ProgettiUiStato.Dati,
    ) {
        val attuale = _stato.value
        if (attuale !is ProgettiUiStato.Dati || attuale.inCorso) return
        _stato.value = pulisci(attuale).copy(inCorso = true)
        scope.launch {
            try {
                when (val esito = withContext(io) { operazione() }) {
                    is Esito.Ok -> aggiorna { it.copy(erroreCrea = null, erroreApri = null) }
                    is Esito.Errore -> aggiorna { alFallimento(it, messaggioPer(esito.errore)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Any other fault of `sessione.apri`/`crea` becomes the one FIXED, plain-Italian
                // message the user sees (never a stack trace) — same rationale as ShellPresenter M1(b).
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiorna { alFallimento(it, MESSAGGIO_ERRORE_GENERICO) }
            } finally {
                // fix-batch-12 #5: reset LAST and UNCONDITIONALLY — Error and Cancellation alike — so
                // a cancelled `crea`/`apri` (e.g. a spurious CancellationException from the port, same
                // family as LettorePresenter's own fix) never leaves `inCorso` stuck true, which would
                // silently block every later crea/apri behind the M3 guard forever.
                aggiorna { it.copy(inCorso = false) }
            }
        }
    }

    private fun aggiorna(f: (ProgettiUiStato.Dati) -> ProgettiUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is ProgettiUiStato.Dati) _stato.value = f(attuale)
    }

    /** H1: dismisses the current `erroreCrea` inline message, if any. */
    fun chiudiErroreCrea() = aggiorna { it.copy(erroreCrea = null) }

    /** H1: dismisses the current `erroreApri` inline message, if any. */
    fun chiudiErroreApri() = aggiorna { it.copy(erroreApri = null) }

    val azioni: AzioniProgetti = AzioniProgetti(
        crea = ::crea,
        apri = ::apri,
        chiudiErroreCrea = ::chiudiErroreCrea,
        chiudiErroreApri = ::chiudiErroreApri,
    )
}
