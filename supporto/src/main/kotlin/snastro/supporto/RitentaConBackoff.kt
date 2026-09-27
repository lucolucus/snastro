package snastro.supporto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

/**
 * A background worker that runs [lavoro] once per requested key (ADR 0028 §2).
 *
 * - Keys requested before their run coalesce (by `equals`) over a conflated signal: one run each.
 * - A run returning false, or throwing an exception, is reported through [segnalazione] with the key and
 *   the cause (null for false), then retried after a bounded exponential backoff: [attesaIniziale],
 *   doubling up to [attesaMassima]. A failed key is retried until it succeeds or the scope is cancelled;
 *   its recovery is reported once.
 * - Every [Error] is rethrown, never reported as a retry: it escapes the worker to its scope's
 *   `CoroutineExceptionHandler`. A [CancellationException] is rethrown ONLY when the worker's own coroutine is
 *   no longer active (its own scope/[Job] was cancelled): a FOREIGN one raised inside [lavoro] — `withTimeout`
 *   expiring, `await` on an already-cancelled [kotlinx.coroutines.Deferred] — is a failure like any other,
 *   reported and retried (a2, ADR 0028 §7.3 step 3).
 *
 * Runs are sequential, on the scope given to [avvia].
 */
public class RitentaConBackoff<K>(
    private val lavoro: suspend (K) -> Boolean,
    private val segnalazione: Segnalazione,
    private val attesaIniziale: Duration,
    private val attesaMassima: Duration,
) {
    init {
        require(attesaIniziale.isPositive()) { "attesaIniziale must be positive: $attesaIniziale" }
        require(attesaMassima >= attesaIniziale) { "attesaMassima $attesaMassima < attesaIniziale $attesaIniziale" }
    }

    private val richieste = LinkedHashSet<K>()
    private val segnale = Channel<Unit>(Channel.CONFLATED)

    /** Starts the worker on [scope]; cancelling [scope] (or the returned [Job]) stops it and its pending retries. */
    public fun avvia(scope: CoroutineScope): Job = scope.launch {
        val fallimenti = HashMap<K, Int>()
        val ritenti = HashMap<K, Job>()
        while (true) {
            segnale.receive()
            for (chiave in prendiRichieste()) {
                ritenti.remove(chiave)?.cancel() // runs now: the scheduled retry is superseded
                val fallimento = esegui(chiave)
                ensureActive() // cancelled while running: no report, no retry
                if (fallimento == null) {
                    fallimenti.remove(chiave)?.let { n -> segnala("riuscito dopo $n tentativi falliti", chiave, null) }
                } else {
                    val n = (fallimenti[chiave] ?: 0) + 1
                    fallimenti[chiave] = n
                    val attesa = attesaDopo(n)
                    segnala("fallito (tentativo $n), nuovo tentativo tra $attesa", chiave, fallimento.eccezione)
                    ritenti[chiave] = launch {
                        delay(attesa)
                        richiedi(chiave)
                    }
                }
            }
        }
    }

    /** Asks for one run of [lavoro] for [chiave]; thread-safe, never blocks. */
    public fun richiedi(chiave: K) {
        synchronized(richieste) { richieste.add(chiave) }
        segnale.trySend(Unit)
    }

    private fun prendiRichieste(): List<K> = synchronized(richieste) {
        richieste.toList().also { richieste.clear() }
    }

    /** Null when done; otherwise the failure, with its exception (null for a false return). */
    @Suppress("TooGenericExceptionCaught") // every non-fatal exception is reported and retried (ADR 0028 §2)
    private suspend fun esegui(chiave: K): Fallimento? =
        try {
            if (lavoro(chiave)) null else Fallimento(null)
        } catch (e: CancellationException) {
            // AC-C91: the worker's OWN cancellation (its Job no longer active) must still stop the worker; a
            // FOREIGN one (raised inside lavoro while this Job is still active — withTimeout, await on an
            // already-cancelled Deferred) is a failure, not a rethrow.
            if (coroutineContext[Job]?.isActive == true) Fallimento(e) else throw e
        } catch (e: Exception) {
            Fallimento(e)
        }

    private fun attesaDopo(fallimenti: Int): Duration {
        var attesa = attesaIniziale
        repeat(fallimenti - 1) { attesa = minOf(attesa * 2, attesaMassima) }
        return attesa
    }

    private fun segnala(esito: String, chiave: K, causa: Throwable?) {
        segnalazione.segnala("RitentaConBackoff: lavoro per '$chiave' $esito", causa)
    }

    private class Fallimento(val eccezione: Exception?)
}
