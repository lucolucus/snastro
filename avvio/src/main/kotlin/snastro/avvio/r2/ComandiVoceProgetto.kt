package snastro.avvio.r2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runInterruptible
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.applicazione.comandi.SaltaVoce
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.dominio.TipoParlante
import snastro.ui.registrazione.ComandiVoce
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ErroreComandoVoce
import snastro.ui.registrazione.FraseRef
import snastro.ui.registrazione.PassiNominaFrase
import snastro.ui.registrazione.StatoComando
import java.time.Clock
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [ComandiVoce] of the open project (ADR 0017 §3, AC-418/AC-419/AC-420): every card command runs in
 * ITS OWN child of [progetto] — a [SupervisorJob], so one failing command never cancels another nor the
 * project's other workers — never in the S3 screen's scope: leaving S3 does not cancel it (AC-415),
 * closing the project does (the session scope is [progetto]'s parent, `CollaboratoriR2.ferma` joins it).
 *
 * - The job is REGISTERED (and in [stato]) BEFORE it starts ([LavoriPerChiave]): an [annulla] right
 *   after the click is never lost.
 * - [esecutore] is the command body; in the app it is [eseguiConServizi]: the blocking service under
 *   `runInterruptible(bg)`, so [annulla] interrupts a thread waiting on the native Mutex
 *   (`lockInterruptibly`, ADR 0017 §1.4) and nothing is written (the wait happens before any transaction).
 * - A body that THROWS becomes `Esito.Errore(ErroreComandoVoce.NonRiuscito)` (logged): the card shows an
 *   inline message and the port stays usable.
 * - At most one command per [VoceRef]: S3 disables a pending card (AC-411), so a second [esegui] for a
 *   [VoceRef] already running JOINS that one instead of starting a second write.
 * - ADR 0019 §5: [nominaFrase] is the same machinery keyed by [FraseRef] ([statoFrasi], [annullaFrase]),
 *   with [esecutoreFrase] as the body — in the app [eseguiFraseConServizi].
 */
internal class ComandiVoceProgetto(
    progetto: CoroutineScope,
    clock: Clock,
    private val esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    private val esecutoreFrase: suspend (FraseRef, PassiNominaFrase) -> Esito<Unit> = { _, _ -> Esito.Ok(Unit) },
) : ComandiVoce {
    private val scope = CoroutineScope(progetto.coroutineContext + SupervisorJob(progetto.coroutineContext[Job]))
    private val voci = LavoriPerChiave<VoceRef>(scope, clock)
    private val frasi = LavoriPerChiave<FraseRef>(scope, clock)
    override val stato: StateFlow<Map<VoceRef, StatoComando>> = voci.stato
    override val statoFrasi: StateFlow<Map<FraseRef, StatoComando>> = frasi.stato

    override suspend fun esegui(comando: ComandoVoce): Esito<Unit>? =
        voci.esegui(comando.voceRef) {
            protetto("comando ${comando::class.simpleName} su ${comando.voceRef}") { esecutore(comando) }
        }

    override fun annulla(voceRef: VoceRef) = voci.annulla(voceRef)

    /** ADR 0019 §5: the naming steps of [segmentoId], same scope/pending state/cancellation as [esegui]. */
    override suspend fun nominaFrase(
        registrazioneId: RegistrazioneId,
        segmentoId: SegmentoId,
        passi: PassiNominaFrase,
    ): Esito<Unit>? {
        val ref = FraseRef(registrazioneId, segmentoId)
        return frasi.esegui(ref) {
            protetto("nominaFrase ${passi::class.simpleName} su $ref") { esecutoreFrase(ref, passi) }
        }
    }

    override fun annullaFrase(frase: FraseRef) = frasi.annulla(frase)

    private suspend fun protetto(descrizione: String, corpo: suspend () -> Esito<Unit>): Esito<Unit> = try {
        corpo()
    } catch (e: CancellationException) {
        throw e
    } catch (
        // A decode/extraction/SQL fault (ADR 0003): nothing was committed; the card shows it, the port lives on.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        log.log(Level.WARNING, "$descrizione fallito", e)
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

        /**
         * The app's naming body (ADR 0019 §5): the [PassiNominaFrase] steps as SEPARATE commands, in order,
         * all inside ONE `runInterruptible(bg)` — a cancellation interrupts the step waiting on the native
         * Mutex (the `ConfermaAttribuzione` extraction) and skips the ones not yet run; the committed ones stay.
         * Glue only: which steps is the S3 presenter's decision, every rule is its command's.
         */
        fun eseguiFraseConServizi(
            bg: CoroutineDispatcher,
            servizi: ServiziFrase,
        ): suspend (FraseRef, PassiNominaFrase) -> Esito<Unit> = { frase, passi ->
            runInterruptible(bg) { servizi.esegui(frase, passi) }
        }

        private fun TipoParlanteVista.dominio(): TipoParlante = when (this) {
            TipoParlanteVista.RICORRENTE -> TipoParlante.RICORRENTE
            TipoParlanteVista.OCCASIONALE -> TipoParlante.OCCASIONALE
        }
    }
}
