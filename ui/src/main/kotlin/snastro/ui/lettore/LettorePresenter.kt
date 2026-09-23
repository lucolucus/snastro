package snastro.ui.lettore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE

/**
 * State holder of the shared audio bar (RC-2, thin UI): one instance is shared by every screen that
 * embeds [BarraLettore] (`:avvio` wires it once) — `:ui` never re-decides [LettoreAudio.disponibile] or
 * how an estratto sequences its intervals (RC-1), it only reflects [LettoreAudio.stato].
 *
 * Every [LettoreAudio] call (`disponibile`, `riproduciDa`/`riproduciEstratto`, `pausa`) runs on a
 * single-lane view of [io] (rework HIGH-1): over the real `Dispatchers.IO` a slow `riproduci(A)` and a
 * later `riproduci(B)` would otherwise be free to run concurrently and finish in either order, letting
 * A's late completion overwrite B's even though `idAtteso` already moved on. [richiestaCorrente] tags
 * every request so a superseded one is also dropped at its own completion, even where cancelling its
 * `Job` cannot interrupt a port call already in flight (a plain, non-suspending call has no suspension
 * point to cancel at).
 */
class LettorePresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val lettore: LettoreAudio,
) {
    private val io: CoroutineDispatcher = io.limitedParallelism(1)

    private val _stato = MutableStateFlow<LettoreUiStato>(LettoreUiStato.Inattivo)
    val stato: StateFlow<LettoreUiStato> = _stato.asStateFlow()

    // AC-191: the id the presenter is waiting for `lettore.stato` to catch up to — `null` while idle,
    // unavailable, or while a request's own `disponibile` check is still in flight (see `avvia`). Gates
    // stray emissions from a playback already superseded by a newer request.
    private var idAtteso: RegistrazioneId? = null

    // HIGH-1: bumped on every `avvia`; a request compares its own value against this one before
    // touching `idAtteso`/`_stato`, so a superseded request's outcome (success, unavailability or fault)
    // never overwrites a newer one, regardless of the actual finish order of the port calls.
    private var richiestaCorrente = 0L
    private var comandoInCorso: Job? = null

    init {
        scope.launch {
            lettore.stato.collect { s ->
                if (s.registrazioneId != null && s.registrazioneId == idAtteso) {
                    _stato.value = LettoreUiStato.Pronto(s.posizioneMs, s.inRiproduzione)
                }
            }
        }
    }

    /** Plays [id] from [daMs] (AC-187), or replaces the playback already in progress (AC-190). */
    fun riproduci(id: RegistrazioneId, daMs: Long = 0) = avvia(id) { lettore.riproduciDa(id, daMs) }

    /** Plays [estratto]'s intervals in sequence (AC-188), replacing any playback in progress (AC-190). */
    fun riproduciEstratto(estratto: EstrattoRef) =
        avvia(estratto.registrazioneId) { lettore.riproduciEstratto(estratto) }

    private fun avvia(id: RegistrazioneId, comando: () -> Unit) {
        val richiesta = ++richiestaCorrente
        comandoInCorso?.cancel()
        // Closes the window LOW-1 opens by moving `disponibile` off the calling thread: without this, a
        // stray tick for the PREVIOUS id could still match `idAtteso` while this request's own
        // `disponibile` check is still in flight and wrongly resolve this Caricamento.
        idAtteso = null
        _stato.value = LettoreUiStato.Caricamento
        comandoInCorso = scope.launch {
            var esito: LettoreUiStato? = null
            var nuovoIdAtteso: RegistrazioneId? = null
            try {
                if (!withContext(io) { lettore.disponibile(id) }) { // LOW-1: off the UI thread
                    // MED-2: a previous source may still be playing — don't strand it with no pause control.
                    withContext(io) { lettore.pausa() }
                    esito = LettoreUiStato.NonDisponibile(MESSAGGIO_SORGENTE_NON_DISPONIBILE)
                } else {
                    nuovoIdAtteso = id
                    withContext(io) { comando() }
                    // MED-1: `lettore.stato` is a StateFlow — it never re-emits a value equal to the one it
                    // already holds, so an identical repeated request would otherwise never resolve and the
                    // collector above would never fire for it.
                    val s = lettore.stato.value
                    if (s.registrazioneId == id) esito = LettoreUiStato.Pronto(s.posizioneMs, s.inRiproduzione)
                    // else: left unresolved here on purpose — the `init` collector resolves it once
                    // `lettore.stato` reports this id, the ordinary asynchronous-player path.
                }
            } catch (
                // fix-batch-12 #7: `ensureActive()` rethrows exactly when THIS job is really
                // cancelled (the normal path, left alone below) — a `CancellationException` thrown
                // by the port itself while the job is still active is not a real cancellation and
                // must not leave `_stato` stuck on Caricamento (nothing else would ever resolve it).
                @Suppress("SwallowedException") e: CancellationException,
            ) {
                ensureActive()
                nuovoIdAtteso = null
                esito = LettoreUiStato.NonDisponibile(MESSAGGIO_ERRORE_GENERICO)
            } catch (
                // HIGH-2: `disponibile`/`riproduciDa`/`riproduciEstratto`/`pausa` may throw (e.g.
                // LineUnavailableException, IOException) — mapped to the one fixed message rather than
                // leaving the bar stuck on Caricamento or letting the fault escape `scope.launch` and take
                // the `stato` collector down with it (same rationale as ShellPresenter/ProgettiPresenter).
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                nuovoIdAtteso = null
                esito = LettoreUiStato.NonDisponibile(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                // HIGH-1/HIGH-2: only the still-current request may touch shared state — a superseded one
                // (cancelled, or merely finished late) is dropped here even where cancellation alone could
                // not stop its already-running port call. This is also the ONE place `_stato`/`idAtteso`
                // are written from this request, so it can never leave them out of sync with each other —
                // guaranteeing Caricamento is never left stuck by this request's own outcome.
                if (richiesta == richiestaCorrente) {
                    idAtteso = nuovoIdAtteso
                    if (esito != null) _stato.value = esito
                }
            }
        }
    }

    /** Pauses the current playback, if any (AC-187). */
    fun pausa() {
        scope.launch { withContext(io) { lettore.pausa() } }
    }

    val azioni: AzioniLettore = AzioniLettore(
        riproduci = ::riproduci,
        riproduciEstratto = ::riproduciEstratto,
        pausa = ::pausa,
    )
}
