package snastro.avvio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snastro.kernel.Esito
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.Executors

/**
 * The serial pipeline dispatcher (ADR 0004, AC-233/234/235/312/313/314): a dedicated single-thread
 * worker that drives `EseguiProssimaElaborazione` (`esegui-elaborazione`) over the FIFO of
 * `in_attesa` Elaborazioni, holds while the ML models are not provisioned, and self-heals after a
 * crash or an escape via `RecuperaElaborazioniInterrotte`.
 *
 * It knows nothing about `:trascrizione`'s or `:modelli`'s own types: the three collaborators it
 * needs are plain functions — the Published-Language boundary this block owns is exactly their
 * shape, not any concrete class. `avvio-composizione` binds them, e.g.
 * `eseguiProssimaElaborazione = eseguiServizio::esegui` partially applied to the
 * `EseguiProssimaElaborazione` command object (`{ eseguiServizio.esegui(EseguiProssimaElaborazione) }`),
 * likewise for `RecuperaElaborazioniInterrotte`, and `modelliPronti = provisioningModelli::pronti`.
 *
 * **Constructor / lifecycle.** [scope] is the SESSION's own child [CoroutineScope]
 * (`:avvio` `SessioneProgettoImpl.apriGrafo`'s `scopeSessione`, cancelled by
 * `SessioneProgettoImpl.chiudi` — dev-architecture `#pacchetti`): one [CodaElaborazioni] per open
 * Progetto. Construction immediately launches the worker on a dedicated single-thread
 * [CoroutineDispatcher] (a real OS thread by default; `dispatcherSingoloThread` is a seam for
 * deterministic tests) and starts it: [recuperaElaborazioniInterrotte] runs FIRST (AC-233), before
 * any attempt to advance the queue. Since a NEW [CodaElaborazioni] is built per project-open,
 * reopening a project without restarting the app reruns [recuperaElaborazioniInterrotte] by
 * construction alone (AC-312's "riaperto senza riavviare l'app") — no extra code needed for that
 * half of the AC. **Stopped** simply by [scope] being cancelled (nothing else to call): the
 * worker's job then closes the dedicated executor ([invokeOnCompletion][Job.invokeOnCompletion]).
 *
 * **Advancing.** [avanza] is the only external trigger: call it whenever something may have changed
 * that could unblock the queue (a new Elaborazione was queued, the models finished downloading).
 * Concurrent calls are coalesced onto one pending request ([Channel.CONFLATED]): the dedicated
 * single thread processes at most one at a time, so concurrent [avanza] calls never start two
 * Elaborazioni together (AC-314) — the `in_attesa → in_corso` transition has no version check and
 * relies on exactly this single-thread guarantee (never construct two [CodaElaborazioni] over the
 * same database). The worker also re-arms itself a short, CONSTANT delay ([INTERVALLO_CONTROLLO])
 * after every attempt — never a zero-delay loop — so it keeps polling on its own while the models
 * are still missing (AC-235: `ProvisioningModelli` exposes only the query `pronti()`, no push
 * signal to subscribe to) and while there may be more `in_attesa` work to drain after one
 * Elaborazione turns terminal (AC-234), without requiring `avvio-composizione` to fire one external
 * [avanza] per queued item.
 *
 * **Never dies (AC-312).** Every call to [eseguiProssimaElaborazione] (and to
 * [recuperaElaborazioniInterrotte]) runs through [eseguiProtetto]: a cancellation, an interrupt (the
 * flag is restored), an [Error] (`OutOfMemoryError` included) or any unexpected exception is caught
 * and treated as "the run was interrupted" — never rethrown into the worker coroutine. Only [scope]
 * itself being cancelled stops the loop: that cancellation is observed at the `delay`/channel-receive
 * suspension points, OUTSIDE [eseguiProtetto], so it is never accidentally swallowed. On an escape,
 * [recuperaElaborazioniInterrotte] runs before the next attempt (the escaped run may have left an
 * Elaborazione `in_corso`).
 *
 * **A stuck head never spins (AC-313, F-G).** When [eseguiProssimaElaborazione] returns an
 * `Esito.Errore` (e.g. a synchronous subscriber that always refuses `ElaborazioneAvviata`, dooming
 * the `in_attesa → in_corso` transaction every time), the worker backs off with a GROWING delay and
 * retries up to [MAX_TENTATIVI_TESTA] times; once exhausted it sets [bloccata] and calls
 * [segnalaCodaBloccata] exactly once — the head then stays `in_attesa` for the rest of THIS session
 * (no further automatic attempts; [avanza] becomes a no-op): a persistently refused head is a
 * session-scoped business fact this dispatcher does not fight forever, but it never crashes or
 * hangs the app over it either.
 */
internal class CodaElaborazioni(
    scope: CoroutineScope,
    private val eseguiProssimaElaborazione: () -> Esito<Unit>,
    private val recuperaElaborazioniInterrotte: () -> Esito<Unit>,
    private val modelliPronti: () -> Boolean,
    private val segnalaCodaBloccata: () -> Unit = {},
    dispatcherSingoloThread: CoroutineDispatcher = nuovoDispatcherPipeline(),
) {
    private val segnali = Channel<Unit>(Channel.CONFLATED)
    private var tentativiTesta = 0
    private var bloccata = false

    val lavoro: Job = scope.launch(dispatcherSingoloThread) {
        eseguiProtetto(recuperaElaborazioniInterrotte) // AC-233: il recupero gira prima di tutto
        segnali.trySend(Unit) // raccoglie subito un'eventuale coda in_attesa già presente
        segnali.consumeEach { provaAvanzare() }
    }

    init {
        // Lo stop e' l'annullamento di `scope` (H2, dev-architecture): quando il job del worker
        // termina, il canale e l'esecutore dedicato (se e' quello reale, Closeable) vengono chiusi
        // con lui — mai lasciati indietro tra un'apertura e l'altra dello stesso progetto.
        lavoro.invokeOnCompletion {
            segnali.close()
            (dispatcherSingoloThread as? Closeable)?.close()
        }
    }

    /** Richiede un tentativo di avanzamento; coalescente, chiamabile da qualunque thread (AC-314). */
    fun avanza() {
        segnali.trySend(Unit)
    }

    private suspend fun provaAvanzare() {
        if (bloccata) return // AC-313: la testa e' definitivamente bloccata per questa sessione
        if (!modelliPronti()) { // AC-235: la coda resta in attesa finche' i modelli non sono pronti
            riprogramma(INTERVALLO_CONTROLLO)
            return
        }
        when (val risultato = eseguiProtetto(eseguiProssimaElaborazione)) {
            RisultatoProtetto.Sfuggito -> { // AC-312: recupero prima del prossimo elemento
                eseguiProtetto(recuperaElaborazioniInterrotte)
                tentativiTesta = 0
                riprogramma(INTERVALLO_CONTROLLO)
            }

            is RisultatoProtetto.Concluso -> when (risultato.esito) {
                is Esito.Ok -> {
                    tentativiTesta = 0
                    riprogramma(INTERVALLO_CONTROLLO)
                }

                is Esito.Errore -> gestisciAvvioRifiutato() // AC-313
            }
        }
    }

    private suspend fun gestisciAvvioRifiutato() {
        tentativiTesta++
        if (tentativiTesta >= MAX_TENTATIVI_TESTA) {
            bloccata = true
            segnalaCodaBloccata()
        } else {
            riprogramma(backoff(tentativiTesta))
        }
    }

    private suspend fun riprogramma(attesa: Duration) {
        delay(attesa.toMillis())
        segnali.trySend(Unit)
    }

    private companion object {
        val INTERVALLO_CONTROLLO: Duration = Duration.ofSeconds(1)
        val BACKOFF_BASE: Duration = Duration.ofSeconds(1)
        const val MAX_TENTATIVI_TESTA = 3

        /** Esponenziale: 1s, 2s, … — [tentativo] parte da 1. */
        fun backoff(tentativo: Int): Duration = BACKOFF_BASE.multipliedBy(1L shl (tentativo - 1))
    }
}

/** Esito di una chiamata protetta ([eseguiProtetto]): conclusa normalmente, o sfuggita (AC-312). */
private sealed interface RisultatoProtetto {
    data class Concluso(val esito: Esito<Unit>) : RisultatoProtetto
    data object Sfuggito : RisultatoProtetto
}

/**
 * Runs [blocco], never letting a cancellation, an interrupt (flag restored), an [Error]
 * (`OutOfMemoryError` included) or any unexpected exception escape — AC-312 names all of these as
 * things that may "sfuggire a EseguiProssimaElaborazione.esegui" and requires the dispatcher to
 * survive them. Unlike `EseguiProssimaElaborazioneServizio`'s own `eseguiFase` (which RETHROWS
 * cancellation/interrupt so they reach exactly this boundary), here they are the very escape AC-312
 * describes: caught and dropped — no logging sink exists in this codebase for this kind of
 * best-effort failure (same accepted trade-off as `eseguiFase`/`chiudiSilenziosamente`/
 * `fuoriDalThreadUi` in `:avvio`, and `DispatcherEventiInMemoria`'s own broad catch for a
 * symmetrical, already-reviewed reason).
 */
private fun eseguiProtetto(blocco: () -> Esito<Unit>): RisultatoProtetto = try {
    RisultatoProtetto.Concluso(blocco())
} catch (e: InterruptedException) {
    Thread.currentThread().interrupt()
    RisultatoProtetto.Sfuggito
} catch (
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
) {
    RisultatoProtetto.Sfuggito
}

/** The real, production default: one dedicated daemon OS thread (ADR 0004's "single-thread pipeline"). */
private fun nuovoDispatcherPipeline(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pipeline-elaborazioni").apply { isDaemon = true }
    }.asCoroutineDispatcher()
