package snastro.avvio.r2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import snastro.kernel.Esito
import snastro.ui.registrazione.StatoComando
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * One family of project-scoped, cancellable commands keyed by [K] (ADR 0017 §3) — the machinery of
 * [ComandiVoceProgetto], shared by the card commands (per `VoceRef`) and the namings (per `FraseRef`):
 * - a job is REGISTERED (in [stato] too) BEFORE it starts ([CoroutineStart.LAZY]): an [annulla] right
 *   after the click is never lost;
 * - at most one job per key: a second [esegui] for a key already running JOINS that one;
 * - [esegui] returns `null` when the job was cancelled by [annulla] or by the project's close; the CALLER
 *   being cancelled (leaving S3) rethrows and the job goes on.
 */
internal class LavoriPerChiave<K : Any>(private val scope: CoroutineScope, private val clock: Clock) {
    private val lavori = ConcurrentHashMap<K, Deferred<Esito<Unit>>>()
    private val _stato = MutableStateFlow<Map<K, StatoComando>>(emptyMap())
    val stato: StateFlow<Map<K, StatoComando>> = _stato.asStateFlow()

    suspend fun esegui(chiave: K, corpo: suspend () -> Esito<Unit>): Esito<Unit>? {
        val nuovo = scope.async(start = CoroutineStart.LAZY) { corpo() }
        val lavoro = registra(chiave, nuovo)
        return try {
            lavoro.await()
        } catch (e: CancellationException) {
            // Cancelled by `annulla` or by the project's close → `null` (not an error, AC-413/AC-419);
            // the CALLER itself cancelled (leaving S3) → rethrow, the command goes on (AC-415).
            if (currentCoroutineContext().isActive) null else throw e
        }
    }

    fun annulla(chiave: K) {
        lavori[chiave]?.cancel()
    }

    /** [nuovo] registered and started, or — a job already running for [chiave] — that one ([nuovo] dropped). */
    private fun registra(chiave: K, nuovo: Deferred<Esito<Unit>>): Deferred<Esito<Unit>> {
        while (true) {
            val esistente = lavori.putIfAbsent(chiave, nuovo) ?: return avvia(chiave, nuovo)
            if (!esistente.isCompleted) {
                nuovo.cancel()
                return esistente
            }
            lavori.remove(chiave, esistente) // ended, its completion handler not run yet: never join a stale result
        }
    }

    private fun avvia(chiave: K, lavoro: Deferred<Esito<Unit>>): Deferred<Esito<Unit>> {
        val inCorso = StatoComando(clock.instant())
        _stato.update { it + (chiave to inCorso) }
        lavoro.invokeOnCompletion {
            _stato.update { attuale -> if (attuale[chiave] === inCorso) attuale - chiave else attuale }
            lavori.remove(chiave, lavoro)
        }
        lavoro.start()
        return lavoro
    }
}
