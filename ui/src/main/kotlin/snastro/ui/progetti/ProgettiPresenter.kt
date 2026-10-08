package snastro.ui.progetti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.ui.ProgettoAperto
import snastro.ui.SessioneProgetto
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO

/**
 * State holder of S1 · Progetti (RC-2, thin UI): loads [ElencoProgetti] once and forwards `crea`/`apri` to
 * [SessioneProgetto]; the delete flow is [FlussoEliminazione]'s, both run by one [CorsaSingola]. Reapplies the shell's
 * lessons (dev-architecture `#presenter`): an error is a dismissible inline message over the list, never a replacement
 * of it (H1); success is applied from the returned [ProgettoAperto], never left to a collector alone (M1(a) — this
 * presenter has none, but the same "set state from the result" rule keeps `inCorso` from ever getting stuck); a
 * non-cancellation fault becomes the one fixed generic message (M1(b)); a second operation while one is already in
 * flight is ignored (M3). [cartellaUtente], the user's home, is written `~` in the folders the delete flow shows
 * (empty: shown as they are).
 */
class ProgettiPresenter(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val elenco: ElencoProgetti,
    private val sessione: SessioneProgetto,
    cartellaUtente: String,
) {
    private val _stato = MutableStateFlow<ProgettiUiStato>(ProgettiUiStato.Caricamento)
    val stato: StateFlow<ProgettiUiStato> = _stato.asStateFlow()
    private val corsa = CorsaSingola(_stato, scope, io)
    private val eliminazione = FlussoEliminazione(corsa, elenco, sessione, cartellaUtente)

    init {
        scope.launch { caricaElenco() }
    }

    private suspend fun caricaElenco() {
        try {
            val progetti = withContext(io) { elenco.progetti() }
            _stato.value = ProgettiUiStato.Dati(progetti)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // fix-batch-12 #5: the initial load itself was unguarded — a throwing `elenco.progetti()`
            // left `_stato` stuck on Caricamento forever, with crea/apri unreachable (M5's own
            // rationale for RegistrazioniPresenter, applied here). L530d: lands the failure on its OWN
            // `erroreElenco` (a full-width banner + Riprova, [riprova]) — never `erroreCrea`, which is
            // a dismissible per-form message for a FAILED `crea`, a different thing. crea/apri stay
            // usable right away either way (empty `progetti`, `inCorso` false).
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            _stato.value = ProgettiUiStato.Dati(progetti = emptyList(), erroreElenco = MESSAGGIO_ERRORE_GENERICO)
        }
    }

    /** L530d: retries the elenco load after [ProgettiUiStato.Dati.erroreElenco] — shows
     * [ProgettiUiStato.Caricamento] first: a retry with no loading feedback looks like a dead button
     * (the same lesson RegistrazioniPresenter's own M5/L485e fix applies to its `riprova`). */
    fun riprova() {
        _stato.value = ProgettiUiStato.Caricamento
        scope.launch { caricaElenco() }
    }

    private val senzaErroriDiApertura: (ProgettiUiStato.Dati, ProgettoAperto) -> ProgettiUiStato.Dati =
        { stato, _ -> stato.copy(erroreCrea = null, erroreApri = null) }

    fun crea(cartellaGenitore: String, nome: String) = corsa.avvia(
        pulisci = { it.copy(erroreCrea = null) },
        operazione = { sessione.crea(cartellaGenitore, nome) },
        alSuccesso = senzaErroriDiApertura,
        alFallimento = { stato, messaggio -> stato.copy(erroreCrea = messaggio) },
    )

    fun apri(percorso: String) = corsa.avvia(
        pulisci = { it.copy(erroreApri = null) },
        operazione = { sessione.apri(percorso) },
        alSuccesso = senzaErroriDiApertura,
        alFallimento = { stato, messaggio -> stato.copy(erroreApri = messaggio) },
    )

    /** H1: dismisses the current `erroreCrea` inline message, if any. */
    fun chiudiErroreCrea() = corsa.aggiorna { it.copy(erroreCrea = null) }

    /** H1: dismisses the current `erroreApri` inline message, if any. */
    fun chiudiErroreApri() = corsa.aggiorna { it.copy(erroreApri = null) }

    val azioni: AzioniProgetti = AzioniProgetti(
        crea = ::crea,
        apri = ::apri,
        chiudiErroreCrea = ::chiudiErroreCrea,
        chiudiErroreApri = ::chiudiErroreApri,
        riprova = ::riprova,
        chiediElimina = eliminazione::chiedi,
        annullaElimina = eliminazione::annulla,
        confermaElimina = eliminazione::conferma,
        chiudiAvvisoEliminazione = eliminazione::chiudiAvviso,
    )
}
