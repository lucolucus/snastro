package snastro.avvio.r2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import snastro.kernel.Esito
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.applicazione.comandi.SaltaVoce
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.dominio.TipoParlante
import snastro.ui.registrazione.ComandiVoce
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ErroreComandoVoce
import snastro.ui.registrazione.StatoComando
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [ComandiVoce] of the open project (ADR 0017 §3, AC-418/AC-419/AC-420): every card command runs in
 * ITS OWN child of [progetto] — a [SupervisorJob], so one failing command never cancels another nor the
 * project's other workers — never in the S3 screen's scope: leaving S3 does not cancel it (AC-415),
 * closing the project does (the session scope is [progetto]'s parent, `CollaboratoriR2.ferma` joins it).
 *
 * - The job is REGISTERED in [lavori] (and in [stato]) BEFORE it starts ([CoroutineStart.LAZY]): an
 *   [annulla] right after the click is never lost.
 * - [esecutore] is the command body; in the app it is [eseguiConServizi]: the blocking service under
 *   `runInterruptible(bg)`, so [annulla] interrupts a thread waiting on the native Mutex
 *   (`lockInterruptibly`, ADR 0017 §1.4) and nothing is written (the wait happens before any transaction).
 * - A body that THROWS becomes `Esito.Errore(ErroreComandoVoce.NonRiuscito)` (logged): the card shows an
 *   inline message and the port stays usable.
 * - At most one command per [VoceRef]: S3 disables a pending card (AC-411), so a second [esegui] for a
 *   [VoceRef] already running JOINS that one instead of starting a second write.
 */
internal class ComandiVoceProgetto(
    progetto: CoroutineScope,
    private val clock: Clock,
    private val esecutore: suspend (ComandoVoce) -> Esito<Unit>,
) : ComandiVoce {
    private val scope = CoroutineScope(progetto.coroutineContext + SupervisorJob(progetto.coroutineContext[Job]))
    private val lavori = ConcurrentHashMap<VoceRef, Deferred<Esito<Unit>>>()
    private val _stato = MutableStateFlow<Map<VoceRef, StatoComando>>(emptyMap())
    override val stato: StateFlow<Map<VoceRef, StatoComando>> = _stato.asStateFlow()

    override suspend fun esegui(comando: ComandoVoce): Esito<Unit>? {
        val ref = comando.voceRef
        val nuovo = scope.async(start = CoroutineStart.LAZY) { protetto(comando) }
        val lavoro = registra(ref, nuovo)
        return try {
            lavoro.await()
        } catch (e: CancellationException) {
            // Cancelled by `annulla` or by the project's close → `null` (not an error, AC-413/AC-419);
            // the CALLER itself cancelled (leaving S3) → rethrow, the command goes on (AC-415).
            if (currentCoroutineContext().isActive) null else throw e
        }
    }

    override fun annulla(voceRef: VoceRef) {
        lavori[voceRef]?.cancel()
    }

    /** [nuovo] registered and started, or — a command already running for [ref] — that one ([nuovo] dropped). */
    private fun registra(ref: VoceRef, nuovo: Deferred<Esito<Unit>>): Deferred<Esito<Unit>> {
        while (true) {
            val esistente = lavori.putIfAbsent(ref, nuovo) ?: return avvia(ref, nuovo)
            if (!esistente.isCompleted) {
                nuovo.cancel()
                return esistente
            }
            lavori.remove(ref, esistente) // ended, its completion handler not run yet: never join a stale result
        }
    }

    private fun avvia(ref: VoceRef, lavoro: Deferred<Esito<Unit>>): Deferred<Esito<Unit>> {
        val inCorso = StatoComando(clock.instant())
        _stato.update { it + (ref to inCorso) }
        lavoro.invokeOnCompletion {
            _stato.update { attuale -> if (attuale[ref] === inCorso) attuale - ref else attuale }
            lavori.remove(ref, lavoro)
        }
        lavoro.start()
        return lavoro
    }

    private suspend fun protetto(comando: ComandoVoce): Esito<Unit> = try {
        esecutore(comando)
    } catch (e: CancellationException) {
        throw e
    } catch (
        // A decode/extraction/SQL fault (ADR 0003): nothing was committed; the card shows it, the port lives on.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        log.log(Level.WARNING, "comando ${comando::class.simpleName} su ${comando.voceRef} fallito", e)
        Esito.Errore(ErroreComandoVoce.NonRiuscito)
    }

    companion object {
        private val log: Logger = Logger.getLogger(ComandiVoceProgetto::class.java.name)

        /**
         * The app's command body: [ComandoVoce] → `ConfermaAttribuzione` / `SaltaVoce`, run on [bg] through
         * `runInterruptible` (ADR 0017 §3: never on the UI thread; a cancellation interrupts the waiting
         * thread). [conferma]/[salta] are the services built with `eventi.unitaDiLavoro` (AC-359).
         */
        fun eseguiConServizi(
            bg: CoroutineDispatcher,
            conferma: (ConfermaAttribuzione) -> Esito<Unit>,
            salta: (SaltaVoce) -> Esito<Unit>,
        ): suspend (ComandoVoce) -> Esito<Unit> = { comando ->
            runInterruptible(bg) {
                when (comando) {
                    is ComandoVoce.Conferma -> conferma(
                        ConfermaAttribuzione(
                            comando.voceRef,
                            ObiettivoAttribuzione.ParlanteEsistente(comando.parlanteId),
                        ),
                    )
                    is ComandoVoce.Nuovo -> conferma(
                        ConfermaAttribuzione(
                            comando.voceRef,
                            ObiettivoAttribuzione.NuovoParlante(comando.nome, comando.tipo.dominio()),
                        ),
                    )
                    is ComandoVoce.Salta -> salta(SaltaVoce(comando.voceRef))
                }
            }
        }

        private fun TipoParlanteVista.dominio(): TipoParlante = when (this) {
            TipoParlanteVista.RICORRENTE -> TipoParlante.RICORRENTE
            TipoParlanteVista.OCCASIONALE -> TipoParlante.OCCASIONALE
        }
    }
}
