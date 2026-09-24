package snastro.ui.registrazione

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import snastro.kernel.Esito
import snastro.kernel.VoceRef
import java.time.Clock
import java.util.Collections

/**
 * Fake [ComandiVoce] (RC-9). Like the real `avvio-parlanti` adapter it runs every command in ITS OWN
 * [scope] (the project's), so cancelling the caller (leaving S3) never cancels the command (AC-415).
 * [risposta] is the command's result. With [trattieni] set, a command waits — as if on the native Mutex
 * — until [rilascia] (the test's latch) or [annulla].
 *
 * Test-only observations: [eseguiti] = the commands that completed with `Ok` ("written"); [annullati] =
 * the commands that saw the cancellation; [thread] = the thread each [esegui] was called on (AC-417).
 */
class ComandiVoceFinta(
    private val scope: CoroutineScope,
    private val clock: Clock,
    var trattieni: Boolean = false,
    private val risposta: suspend (ComandoVoce) -> Esito<Unit> = { Esito.Ok(Unit) },
) : ComandiVoce {
    private val _stato = MutableStateFlow<Map<VoceRef, StatoComando>>(emptyMap())
    override val stato: StateFlow<Map<VoceRef, StatoComando>> = _stato.asStateFlow()

    val eseguiti: MutableList<ComandoVoce> = Collections.synchronizedList(mutableListOf())
    val annullati: MutableList<ComandoVoce> = Collections.synchronizedList(mutableListOf())
    val thread: MutableList<Thread> = Collections.synchronizedList(mutableListOf())

    private val lavori = Collections.synchronizedMap(mutableMapOf<VoceRef, Deferred<Esito<Unit>>>())
    private val cancelli = Collections.synchronizedMap(mutableMapOf<VoceRef, CompletableDeferred<Unit>>())

    override suspend fun esegui(comando: ComandoVoce): Esito<Unit>? {
        thread += Thread.currentThread()
        val ref = comando.voceRef
        val cancello = CompletableDeferred<Unit>().also { if (!trattieni) it.complete(Unit) }
        cancelli[ref] = cancello
        _stato.update { it + (ref to StatoComando(clock.instant())) }
        val lavoro = scope.async {
            try {
                cancello.await()
                risposta(comando).also { if (it is Esito.Ok) eseguiti += comando }
            } catch (e: CancellationException) {
                annullati += comando
                throw e
            } finally {
                _stato.update { it - ref }
                lavori.remove(ref)
                cancelli.remove(ref)
            }
        }
        lavori[ref] = lavoro
        return try {
            lavoro.await()
        } catch (e: CancellationException) {
            // The command was cancelled by `annulla` → `null`; the CALLER being cancelled → rethrow.
            if (currentCoroutineContext().isActive && lavoro.isCancelled) null else throw e
        }
    }

    override fun annulla(voceRef: VoceRef) {
        lavori[voceRef]?.cancel()
    }

    /** Test-only: releases the held command of [voceRef] (the latch). */
    fun rilascia(voceRef: VoceRef) {
        cancelli[voceRef]?.complete(Unit)
    }
}
