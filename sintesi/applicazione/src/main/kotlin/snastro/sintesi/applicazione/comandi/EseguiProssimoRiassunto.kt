package snastro.sintesi.applicazione.comandi

import java.time.Instant

/**
 * Command (actor: sistema, ADR 0023 §2-3, boundary `riassunti-in-coda`): runs the oldest `in_attesa`
 * Riassunto through the local LLM, skipping any id in [esclusi] — a per-session exclusion of a stuck
 * head, mirroring `EseguiProssimaElaborazione` (ADR 0023 §2).
 *
 * [primaDi]: when the shared queue (`:avvio`) arbitrates this source against an Elaborazione source, it
 * passes the OTHER source's head instant here. The claim takes its oldest eligible head (after
 * [esclusi]) only if that head's `richiestoAlle` is STRICTLY BEFORE [primaDi] — an Elaborazione at an
 * equal millisecond wins (ADR 0023 §2). `null` (the default) means no bound: behaviour is unchanged.
 */
public data class EseguiProssimoRiassunto(
    public val esclusi: Set<String> = emptySet(),
    public val primaDi: Instant? = null,
)
