---
id: contesto-lungo-spike
type: spike
side: app
repo: .
depends_on: [runtime-llm-in-app-spike]
resolution: "incontro D-0010 + ADR-0026 §5–§6 (one pass; the 3 h time check is a tests_nl of the Incontro Riassunto block)"
---
# Spike / How is a Registrazione longer than ≈ 1 h 15 summarized?

> Source: context-map "Open spikes" (added 2026-09-25, feature `sintesi`). NOT blocking the first
> release: until it closes, "Riassumi" is refused above the limit (tactical [INV-S6],
> `RegistrazioneTroppoLunga`).

## Question to answer
A `Registrazione` above ≈ 1 h 15 (> ~28k input tokens) does not fit the 32k context. Split +
recompose (per-part results merged into one `Riassunto`), a larger context, or both? The result must
keep every `Fonte` valid (`Verifica delle fonti` unchanged, [INV-S4]), must not duplicate or lose a
`Decisione` across parts, must keep `Decisione`/`QuestioneAperta` apart, and must stay linear in the
budget (≤ ~5 min per hour of audio).

## Closure criterion
A strategy is run on a ≥ 2 h input (real, or two real recordings concatenated) with time, memory and a
user judgement of lost/duplicated `Decisione`s recorded, and an ADR fixes the strategy and the new
limit.

## Unblocks
(block ids pinned by build-manifest) lifting the length limit in the `riassumi` application-service
guard and a split/recompose step in `esegui-riassunto`. First-release blocks do NOT wait on it.
