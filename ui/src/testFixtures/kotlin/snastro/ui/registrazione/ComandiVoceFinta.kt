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
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceRef
import java.time.Clock
import java.util.Collections

/**
 * Fake [ComandiVoce] (RC-9). Like the real `avvio-parlanti` adapter it runs every command in ITS OWN
 * project scope, so cancelling the caller (leaving S3) never cancels the command (AC-415).
 * [risposta] is the command's result (one that throws is `Errore(ErroreComandoVoce.NonRiuscito)`), and
 * [rispostaFrase] a `nominaFrase`'s. With [trattieni] set, a command waits — as if on the native Mutex —
 * until [rilascia] / [rilasciaFrase] (the test's latch) or [annulla] / [annullaFrase].
 *
 * Test-only observations: [eseguiti] = the commands that completed with `Ok` ("written"); [annullati] =
 * the commands that saw the cancellation; [frasi] = every `nominaFrase` received (ref + steps);
 * [thread] = the thread each [esegui] / [nominaFrase] was called on (AC-417, AC-536).
 */
class ComandiVoceFinta(
    scope: CoroutineScope,
    private val clock: Clock,
    var trattieni: Boolean = false,
    private val rispostaFrase: suspend (FraseRef, PassiNominaFrase) -> Esito<Unit> = { _, _ -> Esito.Ok(Unit) },
    private val risposta: suspend (ComandoVoce) -> Esito<Unit> = { Esito.Ok(Unit) },
) : ComandiVoce {
    val eseguiti: MutableList<ComandoVoce> = Collections.synchronizedList(mutableListOf())
    val annullati: MutableList<ComandoVoce> = Collections.synchronizedList(mutableListOf())
    val frasi: MutableList<Pair<FraseRef, PassiNominaFrase>> = Collections.synchronizedList(mutableListOf())
    val thread: MutableList<Thread> = Collections.synchronizedList(mutableListOf())

    private val voci = Lavori<VoceRef>(scope)
    private val lavoriFrasi = Lavori<FraseRef>(scope)
    override val stato: StateFlow<Map<VoceRef, StatoComando>> = voci.stato
    override val statoFrasi: StateFlow<Map<FraseRef, StatoComando>> = lavoriFrasi.stato

    override suspend fun esegui(comando: ComandoVoce): Esito<Unit>? {
        thread += Thread.currentThread()
        return voci.esegui(comando.voceRef, onAnnullato = { annullati += comando }) {
            risposta(comando).also { if (it is Esito.Ok) eseguiti += comando }
        }
    }

    override fun annulla(voceRef: VoceRef) = voci.annulla(voceRef)

    override suspend fun nominaFrase(
        registrazioneId: RegistrazioneId,
        segmentoId: SegmentoId,
        passi: PassiNominaFrase,
    ): Esito<Unit>? {
        thread += Thread.currentThread()
        val ref = FraseRef(registrazioneId, segmentoId)
        frasi += ref to passi
        return lavoriFrasi.esegui(ref, onAnnullato = {}) { rispostaFrase(ref, passi) }
    }

    override fun annullaFrase(frase: FraseRef) = lavoriFrasi.annulla(frase)

    /** Test-only: releases the held command of [voceRef] (the latch). */
    fun rilascia(voceRef: VoceRef) = voci.rilascia(voceRef)

    /** Test-only: releases the held `nominaFrase` of [frase] (the latch). */
    fun rilasciaFrase(frase: FraseRef) = lavoriFrasi.rilascia(frase)

    /** One per-key family of project-scoped, cancellable, optionally held commands. */
    private inner class Lavori<K : Any>(private val scope: CoroutineScope) {
        private val _stato = MutableStateFlow<Map<K, StatoComando>>(emptyMap())
        val stato: StateFlow<Map<K, StatoComando>> = _stato.asStateFlow()
        private val lavori = Collections.synchronizedMap(mutableMapOf<K, Deferred<Esito<Unit>>>())
        private val cancelli = Collections.synchronizedMap(mutableMapOf<K, CompletableDeferred<Unit>>())

        suspend fun esegui(ref: K, onAnnullato: () -> Unit, corpo: suspend () -> Esito<Unit>): Esito<Unit>? {
            val cancello = CompletableDeferred<Unit>().also { if (!trattieni) it.complete(Unit) }
            cancelli[ref] = cancello
            _stato.update { it + (ref to StatoComando(clock.instant())) }
            val lavoro = scope.async {
                try {
                    cancello.await()
                    corpo()
                } catch (e: CancellationException) {
                    onAnnullato()
                    throw e
                } catch (
                    // The contract: a body that throws is an `Errore`, never a dead port (AC-418).
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
                ) {
                    Esito.Errore(ErroreComandoVoce.NonRiuscito)
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

        fun annulla(ref: K) {
            lavori[ref]?.cancel()
        }

        fun rilascia(ref: K) {
            cancelli[ref]?.complete(Unit)
        }
    }
}
