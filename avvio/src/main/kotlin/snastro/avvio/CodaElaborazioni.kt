package snastro.avvio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * The serial pipeline dispatcher (ADR 0004, AC-233/234/235/312/313/314): a dedicated single-thread
 * worker that drives `EseguiProssimaElaborazione` (`esegui-elaborazione`) over the FIFO of
 * `in_attesa` Elaborazioni, holds while the ML models are not provisioned, and self-heals after a
 * crash or an escape via `RecuperaElaborazioniInterrotte`.
 *
 * It knows nothing about `:trascrizione`'s own types (`avvio/src/main` never imports
 * `snastro.trascrizione`, `GrafoR0Test`'s AC-350 guard): the collaborators it needs are plain
 * functions over primitive ids — the Published-Language boundary this block owns is exactly their
 * shape — bundled as [FonteAvanzamento] purely to keep this constructor's own parameter count down
 * (same reason `PortePipeline`/`SessioneProgettoSeams` are bundles elsewhere in `:avvio`).
 * `avvio-composizione` binds them: `prossima` wraps
 * `EseguiProssimaElaborazioneServizio.esegui(EseguiProssimaElaborazione(esclusi))`, mapping its
 * `RisultatoAvanzamento` to [RisultatoTentativo]; `ultimaTentata` reads
 * `EseguiProssimaElaborazioneServizio.ultimaTentata` (needed on an escape — see below);
 * `recuperaElaborazioniInterrotte` calls `RecuperaElaborazioniInterrotteServizio.esegui`;
 * `modelliPronti = provisioningModelli::pronti`.
 *
 * **Constructor / lifecycle.** [scope] is the SESSION's own child [CoroutineScope]
 * (`:avvio` `SessioneProgettoImpl.apriGrafo`'s `scopeSessione`): one [CodaElaborazioni] per open
 * Progetto. Construction immediately launches the worker on a dedicated single-thread
 * [CoroutineDispatcher] (a real OS thread by default; `dispatcherSingoloThread` is a seam for
 * deterministic tests) and starts it: [recuperaElaborazioniInterrotte] runs FIRST (AC-233), before
 * any attempt to advance the queue. Since a NEW [CodaElaborazioni] is built per project-open,
 * reopening a project without restarting the app reruns it by construction alone (AC-312's
 * "riaperto senza riavviare l'app") — no extra code needed for that half of the AC.
 *
 * **Stopped — TWO steps, in order (rework item 3):** (1) `avvio-composizione` cancels [scope] (it
 * already does, in `SessioneProgettoImpl.chiudi`'s `finally`); (2) it then calls [fermaEAttendi],
 * BEFORE closing the database. Cancelling [scope] alone is not enough on its own: a blocking
 * `esegui`/`recupera` call may be mid-flight for minutes (the real ML pipeline), and every such call
 * runs through [runInterruptible] precisely so cancelling [scope] interrupts the dedicated thread —
 * but the composition still has to WAIT for the worker to actually unwind (and for the dedicated
 * executor to close, [Job.invokeOnCompletion]) before the database handle underneath it goes away;
 * otherwise a blocking call could still be touching it. [fermaEAttendi] is that wait, with a timeout.
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
 * signal) and while there may be more `in_attesa` work to drain after one Elaborazione turns
 * terminal (AC-234), without requiring `avvio-composizione` to fire one external [avanza] per item.
 *
 * **Never dies on an ordinary escape (AC-312).** Every blocking call runs through [eseguiProtetto]
 * + [runInterruptible]: an interrupt (flag CLEARED, never restored — rework item 4: this thread
 * keeps running, so it must be clean for its NEXT blocking call, or that one fails immediately too)
 * or any unexpected `Throwable` (`Error`/`OutOfMemoryError` included) is caught and treated as "the
 * run was interrupted" — UNLESS [scope] is genuinely no longer active, in which case it is a REAL
 * stop request, not a survivable escape, and is rethrown so the worker coroutine actually ends
 * (rework item 3). On a survived escape, recovery runs before the next attempt (the escaped run may
 * have left an Elaborazione `in_corso`), and — since [ultimaTentata] reads the SUPPLIER-side id
 * captured the moment a FIFO head was picked, BEFORE running anything risky — the escaped id is
 * still readable right after catching the escape, letting it count toward that head's exclusion
 * like a refusal (rework item 2, below).
 *
 * **A stuck head never halts the queue (AC-313, rework item 1 — "gli altri elementi proseguono").**
 * [FonteAvanzamento.prossima] reports which id it attempted via [RisultatoTentativo] even when the
 * `in_attesa → in_corso` transaction is REFUSED (e.g. a synchronous subscriber that always refuses
 * `ElaborazioneAvviata`): [RisultatoTentativo.Rifiutata]. A refusal and an escape on the SAME id
 * both grow the SAME per-id counter (never resetting each other to 0, rework item 2); after
 * [MAX_TENTATIVI_PER_ID] (with a GROWING delay between attempts, never a zero-delay spin) that id is
 * added to a per-session exclusion set passed back into every further [FonteAvanzamento.prossima]
 * call — [segnalaElaborazioneBloccata] fires ONCE for it — and the head simply stays `in_attesa` for
 * the rest of the session: it is never retried again automatically, but every OTHER eligible
 * `in_attesa` Elaborazione keeps draining normally, because the supplier always picks the oldest
 * ELIGIBLE (non-excluded) head, not blindly the oldest overall.
 */
internal class CodaElaborazioni(
    scope: CoroutineScope,
    private val fonte: FonteAvanzamento,
    private val recuperaElaborazioniInterrotte: () -> Unit,
    private val modelliPronti: () -> Boolean,
    private val segnalaElaborazioneBloccata: (String) -> Unit = {},
    dispatcherSingoloThread: CoroutineDispatcher = nuovoDispatcherPipeline(),
) {
    private val segnali = Channel<Unit>(Channel.CONFLATED)
    private val esclusi = mutableSetOf<String>()
    private val tentativi = mutableMapOf<String, Int>()

    val lavoro: Job = scope.launch(dispatcherSingoloThread) {
        eseguiProtetto { recuperaElaborazioniInterrotte() } // AC-233
        segnali.trySend(Unit) // raccoglie subito un'eventuale coda in_attesa già presente
        segnali.consumeEach { provaAvanzare() }
    }

    init {
        lavoro.invokeOnCompletion {
            segnali.close()
            (dispatcherSingoloThread as? Closeable)?.close()
        }
    }

    /** Richiede un tentativo di avanzamento; coalescente, chiamabile da qualunque thread (AC-314). */
    fun avanza() {
        segnali.trySend(Unit)
    }

    /**
     * Blocking stop (rework item 3): call AFTER cancelling [scope] and BEFORE closing the database.
     * Waits for [lavoro] to finish unwinding (its `finally`s, the dedicated executor's close) up to
     * [timeoutMs]. Returns `true` if it finished in time, `false` if the timeout elapsed first (the
     * dedicated thread may still be finishing in the background — best-effort by the caller, never
     * throws).
     */
    fun fermaEAttendi(timeoutMs: Long = TIMEOUT_STOP_MS): Boolean = runBlocking {
        withTimeoutOrNull(timeoutMs) { lavoro.join() } != null
    }

    private suspend fun provaAvanzare() {
        if (!modelliPronti()) { // AC-235: la coda resta in attesa finche' i modelli non sono pronti
            riprogramma(INTERVALLO_CONTROLLO)
            return
        }
        when (val risultato = eseguiProtetto { fonte.prossima(esclusi.toSet()) }) {
            is RisultatoProtetto.Sfuggito -> { // AC-312: recupero prima del prossimo elemento
                eseguiProtetto { recuperaElaborazioniInterrotte() }
                fonte.ultimaTentata()?.let { gestisciFallimento(it) } ?: riprogramma(INTERVALLO_CONTROLLO)
            }

            is RisultatoProtetto.Concluso -> when (val esito = risultato.valore) {
                RisultatoTentativo.Nessuno -> riprogramma(INTERVALLO_CONTROLLO)
                is RisultatoTentativo.Avviata -> {
                    tentativi.remove(esito.id) // AC-313: un avvio riuscito pulisce lo storico dell'id
                    riprogramma(INTERVALLO_CONTROLLO)
                }

                is RisultatoTentativo.Rifiutata -> gestisciFallimento(esito.id)
            }
        }
    }

    /**
     * AC-313 + rework item 2: [id]'s counter (shared by refusals AND escapes, never reset except by
     * a real [RisultatoTentativo.Avviata]) grows with a back-off between retries; once it reaches
     * [MAX_TENTATIVI_PER_ID], [id] joins [esclusi] for the rest of the session — the supplier then
     * skips it and picks the next eligible head, so the rest of the queue keeps draining.
     */
    private suspend fun gestisciFallimento(id: String) {
        val tentativo = (tentativi[id] ?: 0) + 1
        if (tentativo >= MAX_TENTATIVI_PER_ID) {
            tentativi.remove(id)
            esclusi += id
            segnalaElaborazioneBloccata(id)
            riprogramma(INTERVALLO_CONTROLLO) // riparte subito sul prossimo elemento idoneo
        } else {
            tentativi[id] = tentativo
            riprogramma(backoff(tentativo))
        }
    }

    private suspend fun riprogramma(attesa: Duration) {
        delay(attesa.toMillis())
        segnali.trySend(Unit)
    }

    private companion object {
        val INTERVALLO_CONTROLLO: Duration = Duration.ofSeconds(1)
        val BACKOFF_BASE: Duration = Duration.ofSeconds(1)
        const val MAX_TENTATIVI_PER_ID = 3
        const val TIMEOUT_STOP_MS = 5_000L

        /** Esponenziale: 1s, 2s, … — [tentativo] parte da 1. */
        fun backoff(tentativo: Int): Duration = BACKOFF_BASE.multipliedBy(1L shl (tentativo - 1))
    }
}

/**
 * [CodaElaborazioni]'s only two collaborators that need to agree on WHICH id was attempted —
 * bundled to keep the constructor's own parameter count under detekt's `LongParameterList`
 * (mirrors `PortePipeline`/`SessioneProgettoSeams`). [prossima] attempts the oldest eligible FIFO
 * head (excluding [esclusi]) and reports the outcome; it may also let a `Throwable` escape (AC-312).
 * [ultimaTentata] is the side-channel read right after catching such an escape — the id can't travel
 * through a thrown exception's type, so the supplier remembers it (mirrors
 * `EseguiProssimaElaborazioneServizio.ultimaTentata`, `:trascrizione:applicazione`).
 */
internal class FonteAvanzamento(
    val prossima: (esclusi: Set<String>) -> RisultatoTentativo,
    val ultimaTentata: () -> String?,
)

/**
 * Outcome of one [FonteAvanzamento.prossima] attempt, over PRIMITIVE ids only (never a
 * `:trascrizione` type — AC-350's guard on `avvio/src/main`). `internal` so `avvio-composizione`'s
 * wiring (same module, a different file) can build one.
 */
internal sealed interface RisultatoTentativo {
    /** No `in_attesa` Elaborazione was eligible: the queue is empty, or every head is excluded. */
    data object Nessuno : RisultatoTentativo

    /** [id] was picked, started, and run through the pipeline (whatever ITS OWN outcome turns out to be). */
    data class Avviata(val id: String) : RisultatoTentativo

    /** [id]'s `in_attesa → in_corso` transaction was refused; it is still `in_attesa`. */
    data class Rifiutata(val id: String) : RisultatoTentativo
}

/** Esito di una chiamata protetta ([eseguiProtetto]): conclusa normalmente, o sfuggita (AC-312). */
private sealed interface RisultatoProtetto<out T> {
    data class Concluso<T>(val valore: T) : RisultatoProtetto<T>
    data object Sfuggito : RisultatoProtetto<Nothing>
}

/**
 * Runs [blocco] through [runInterruptible] (rework item 3 — so cancelling the worker's scope
 * interrupts the dedicated thread if it is mid-call). A cancellation, an interrupt (flag CLEARED —
 * rework item 4, this thread keeps running so it must be clean for its next call), an [Error]
 * (`OutOfMemoryError` included) or any unexpected exception is caught and treated as "the run was
 * interrupted" — UNLESS the caller's own coroutine is no longer active, in which case this is a REAL
 * stop request (not a survivable escape) and is rethrown so the worker actually ends. No logging
 * sink exists in this codebase for the survived-escape case (same accepted trade-off as
 * `eseguiFase`/`chiudiSilenziosamente`/`fuoriDalThreadUi` in `:avvio`, and
 * `DispatcherEventiInMemoria`'s own broad catch for a symmetrical, already-reviewed reason).
 */
private suspend fun <T> eseguiProtetto(blocco: () -> T): RisultatoProtetto<T> = try {
    RisultatoProtetto.Concluso(runInterruptible { blocco() })
} catch (e: InterruptedException) {
    Thread.interrupted() // rework item 4: CLEAR, never restore — this thread runs more work right after
    if (!coroutineContext.isActive) throw CancellationException("coda fermata", e)
    RisultatoProtetto.Sfuggito
} catch (
    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
) {
    if (!coroutineContext.isActive) throw e
    RisultatoProtetto.Sfuggito
}

/** The real, production default: one dedicated daemon OS thread (ADR 0004's "single-thread pipeline"). */
private fun nuovoDispatcherPipeline(): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pipeline-elaborazioni").apply { isDaemon = true }
    }.asCoroutineDispatcher()
