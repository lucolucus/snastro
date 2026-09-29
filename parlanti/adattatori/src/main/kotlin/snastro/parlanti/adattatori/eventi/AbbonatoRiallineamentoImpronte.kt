package snastro.parlanti.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.comandi.RiallineaImpronte
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.supporto.RitentaConBackoff
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `AbbonatoDopoCommit` (ADR 0012) that keeps every Registrazione's print rows fresh after a
 * Trascrizione Revisione (block `abbonato-riallineamento-impronte`, AC-304..AC-307; retry mechanics
 * reworked by `a3-ritenta-parlanti`, ADR 0028 §7.4, AC-C50..C53): [VociUnite]/[VoceDivisa]/
 * [SegmentoRiassegnato] each enqueue a [RiallineaImpronte] for their `registrazioneId`, run only
 * AFTER the publishing command's transaction committed, never on a rollback — its background work
 * is exactly ONE [RitentaConBackoff] (AC-C50: no private conflated loop, backoff or `runCatching`
 * here — [ritenta] is the only place that catches [esegui]'s exceptions), keyed by [RegistrazioneId]
 * so several Registrazioni are retried independently (AC-C51: one failing key never blocks another's
 * progress). A failed run — [Esito.Errore] or a thrown exception — is reported through the injected
 * [Segnalazione] (key + cause) and retried with an exponential backoff, and its later success reports
 * the recovery once (AC-C51); an [Error] escapes to [scope]'s handler instead of being retried, and
 * the worker's own cancellation stops it with no report ([RitentaConBackoff], AC-C53).
 * [RiallineaImpronteServizio] itself is idempotent (INV-15's compare-and-set), so a retried run never
 * touches the Revisione already committed (AC-307) — and the next project opening realigns everything
 * again anyway (`RiallineaTutteLeImpronte`, `avvio-composizione`), so a retry that never succeeds
 * still self-heals.
 *
 * A plain [AbbonatoDopoCommit] VALUE (ADR 0030 §1, AC-C67): it never registers itself, and constructing it
 * launches nothing — its worker runs only once [avvia] is called with the open project's scope (`:avvio`'s
 * `ModuloParlanti`, step 6 of `apriProgetto`). A request received before that is kept and runs at [avvia].
 * Unlike `AbbonatoDocumentoEventi` it has no startup sweep of its own: `RiallineaTutteLeImpronte` at project
 * open is the composition's responsibility.
 */
public class AbbonatoRiallineamentoImpronte(
    private val riallinea: RiallineaImpronteServizio,
    segnalazione: Segnalazione,
    ritardoIniziale: Duration = RITARDO_INIZIALE_DEFAULT,
    ritardoMassimo: Duration = RITARDO_MASSIMO_DEFAULT,
) : AbbonatoDopoCommit {
    private val ritenta = RitentaConBackoff<RegistrazioneId>(::esegui, segnalazione, ritardoIniziale, ritardoMassimo)

    /** Starts the retry worker on [scope]; cancelling [scope] (or the returned [Job]) stops it. */
    public fun avvia(scope: CoroutineScope): Job = ritenta.avvia(scope)

    override fun ricevi(evento: EventoPubblicato) {
        val registrazioneId = when (evento) {
            is VociUnite -> evento.registrazioneId
            is VoceDivisa -> evento.registrazioneId
            is SegmentoRiassegnato -> evento.registrazioneId
            else -> return
        }
        ritenta.richiedi(registrazioneId)
    }

    /** `true` = done, `false` = retry (ADR 0028 §2); [ritenta] is the only catch, never here (AC-C50). */
    private suspend fun esegui(id: RegistrazioneId): Boolean = riallinea.esegui(RiallineaImpronte(id)) is Esito.Ok

    private companion object {
        val RITARDO_INIZIALE_DEFAULT: Duration = 500.milliseconds
        val RITARDO_MASSIMO_DEFAULT: Duration = 30.seconds
    }
}
