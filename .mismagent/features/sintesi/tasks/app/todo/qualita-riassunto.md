---
id: qualita-riassunto-spike
type: spike
side: app
repo: .
depends_on: []
---
# Spike / Is the content of a Riassunto right, beyond structural validity?

> Source: context-map "Open spikes" (added 2026-09-25, feature `sintesi`). Open from the model spike
> (`research/scelta-modello-llm.md`): 116 citations, 0 structural problems, content not judged.

## Question to answer
- Things "da valutare" filed as `Decisione`s instead of `QuestioneAperta`s.
- The same `Fonte`s duplicated across `PuntoChiave`s and `Decisione`s.
- A one-sentence `Sommario` on a short recording (New Recording 4).
- New: the prose and the elements name speakers only through `Voce` tokens (`V<n>`, rendered as the
  current `Nome` at display, tactical [INV-S5]), not as literal names — does that cost quality? How
  often does the model emit an invalid token (dropped and counted by [INV-S4])?
- New (user, 2026-09-25): does the model keep to the per-`Progetto` **lunghezza massima del
  Riassunto** (default 2000 parole, tactical [INV-S9]/[INV-S10])? Measure words written vs the cap
  at ≥ 3 values (e.g. 300, 2000, 2500), whether a low cap loses `Decisione`s/`Azione`s, and confirm
  or re-fix the [300, 2500] bounds.

## Closure criterion
The user judges the `Riassunto`s of ≥ 2 real recordings (`Decisione`s true / missed / invented,
`Decisione` vs `QuestioneAperta` correctly separated, `Responsabile`s right); the `Verifica delle
fonti` drop counts (invalid `Fonte`s, dropped elements, removed bindings, invalid tokens) are
reported; adherence to the lunghezza massima is reported and its bounds confirmed; the prompt +
answer schema v1 (including how the cap is expressed) are fixed in an ADR.

## Unblocks
(block ids pinned by build-manifest) the answer schema v1 of the `ModelloLinguistico` port (the
fake uses a provisional schema until then) and the prompt in the adapter / `esegui-riassunto` input
builder. The riassunto aggregate's [INV-S4] rules do not depend on it.
