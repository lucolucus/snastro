package snastro.trascrizione.applicazione.comandi

import snastro.kernel.ElaborazioneId
import java.time.Instant

/**
 * Command (actor: sistema, R17): runs the oldest `in_attesa` Elaborazione through the local pipeline
 * (AC-68), skipping any id in [esclusi]. No effect if none remains eligible.
 *
 * [esclusi] (AC-313, F-G): the pipeline dispatcher (`:avvio`) grows this set, per session, with the
 * ids of heads whose avvio keeps being refused or keeps escaping — so a stuck head never blocks the
 * rest of the FIFO queue forever; it stays `in_attesa`, just no longer picked, while every other
 * eligible Elaborazione keeps draining normally.
 *
 * [nonDopo] (ADR 0023 §2, boundary `elaborazioni-in-coda`): when the shared queue (`:avvio`) is
 * arbitrating this source against a `Riassunto` source, it passes the OTHER source's head instant as
 * a bound — the claim takes its oldest eligible head (after [esclusi]) only if that head's
 * `creataAlle` is `<= nonDopo` (AC-S21: inclusive). `null` (the default) means no bound: every R0–R2
 * composition, which binds only this source, passes none and behaviour is unchanged (AC-S20). The
 * bound is evaluated INSIDE the claim's own transaction (AC-S22), against the head read there —
 * never against a value read earlier by the coordinator.
 */
public data class EseguiProssimaElaborazione(
    public val esclusi: Set<ElaborazioneId> = emptySet(),
    public val nonDopo: Instant? = null,
)
