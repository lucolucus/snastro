# Open question — avvio-coda-condivisa (READY-FOR-REVIEW head 9e51176, gate green, ADR 0023 check now PASS; parked 2026-09-26)

DEVIATION touching a pinned type: `FonteCoda` (boundary coda-condivisa, pinned with 6 fields) gains a 7th field
`annulla: (registrazioneId: String) -> Unit = {}` — needed to implement `CodaCondivisa.annullaInCorso(tipo, registrazioneId)`
(AC-S63, targeted best-effort cancellation, this block's own Tasks). Additive and defaulted; the Elaborazione source never supplies it.
It is also the hook avvio-sintesi will use for AC-S161 (D-0004: cancel only if the running Riassunto's row is gone).
Alternative: a CodaCondivisa-level hook instead of a FonteCoda field (rework).
Resolution: user accepts → build-manifest adds the field to the pin (and avvio-sintesi spec); block goes to review with no new code.
