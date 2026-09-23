package snastro.parlanti.applicazione.comandi

import snastro.kernel.IntervalloMs

/**
 * TODO(option-c follow-up): PROVISIONAL stand-in for `SorgenteImpronta.chiave` (block sorgente-impronta,
 * ADR 0012 Amendment (b) point 1) until conferma-attribuzione / salta-voce / revisione-policy are reworked
 * to read audio through `SorgenteImpronta`. Encodes the intervals ACTUALLY decoded today, in the given
 * order, with the same canonical text (`"<inizioMs>-<fineMs>"` joined by `","`), so the stored
 * `sorgente_impronta` is exact for what was extracted — and stale, hence refreshed, once the bounded
 * source differs.
 */
internal fun chiaveSorgenteProvvisoria(intervalli: List<IntervalloMs>): String =
    intervalli.joinToString(",") { "${it.inizioMs}-${it.fineMs}" }
