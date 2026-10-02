# letture-parlanti-incontro — contract deviation on a pinned type

Reviewed HEAD e6cdeed1: verifier PASS, code-review APPROVE.
The block reshaped types pinned by boundary `piano-per-somiglianza` (features/trascrizione-con-parlanti/building-blocks.yaml:4022ff):
- PianoRiassegnazione(registrazioneId, …) → PianoRiassegnazione(incontroId, …)
- SpostamentoProposto(segmentoId: SegmentoId, …) → (segmento: SegmentoRef, …)
- order (inizioMs, segmentoId) → (Parte numero, inizio, segmentoId)
Forced by ADR 0035 §6, ADR 0019 Amendment 2026-10-01 and ADR 0033 §1 (SegmentoRef is the only key across Parti). The only consumer (avvio AzioniSomiglianzaProgetto) is updated in the same diff. Outside D-0037 (that covers caller edits, not a block's own pinned output).
Decision needed: accept the pin amendment (record it in the manifest + decisions.md, then integrate) or rework.
