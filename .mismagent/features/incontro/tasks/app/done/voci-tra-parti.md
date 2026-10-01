---
id: voci-tra-parti-spike
type: spike
side: app
repo: .
depends_on: []
central: true
owner: incontro
resolution: "incontro D-0021 (option a); ADR 0036 records the option and the thresholds"
---
# Spike / How do the Voci of different Parti become Voci dell'Incontro?

> Source: context-map "Open spikes" `voci-tra-parti` (added 2026-09-30, feature `incontro` [user D-0002]).
> Evidence so far: [spikes/voci-tra-parti.md](../../../spikes/voci-tra-parti.md) (2026-09-30): one real
> 2-part Incontro, 4 people; with Numero di persone = 4 the `Proposta` gives 4/4 FORTE pairs, one-to-one;
> with the automatic count it is unusable. The pairs are NOT yet verified by ear.
> Working hypothesis modeled in [tactical-model.md](../../../tactical-model.md) (user checkpoint 2026-10-01):
> option (a).

## Question to answer
- Does option (a) hold: a read-model that pairs **unattributed** `Voce`s of **different** `Parte`s of one
  `Incontro` by print similarity, proposing a pair only when it is a mutual, unique FORTE (1:1), confirmed
  by ONE user gesture (`UnisciVoci`), never automatic? Conflicts (two FORTE candidates, a FORTE already
  taken) propose nothing and fall back to manual `unire`.
- Are the proposed pairs right **by ear** (the user listens to the `EstrattoAudio` of each pair)?
- Are `SoglieFascia` calibrated for cross-file comparison, or do they need their own thresholds?
- Confirm the precondition found: a correct `Numero di persone` per `Parte`, asked ONCE per `Incontro`
  and applied to each `Parte` (a `Parte` may have fewer people).

## Closure criterion
The context-map criterion, unchanged:
- on ≥ 2 real `Incontro`s in 2–3 `Parte`s with ≥ 2 people each, the same-person / different-person
  similarity across `Parte`s is measured;
- for option (a) (and any other candidate), the correct / wrong / missed joins are counted, and the time
  is counted in the ADR 0011 budget;
- the user verifies the proposed pairs by ear and judges the effort of joining the `Voce`s of a real
  `Incontro` against "nomina ogni persona una volta sola";
- an ADR records the option, the thresholds if any, and the name of the proposal (analyst: the map leaves
  it pending this spike).

If (a) fails, the manual path (`unire` across `Parti` + name-driven `Proposta di unione`) ships alone:
it does not depend on this spike.

## Unblocks
(block ids pinned by build-manifest) ONLY the Parlanti read-model of the print-based cross-Parte proposal
(and the UI element that shows it). NOT `unire` across `Parte`s, NOT the `Proposta di unione`, NOT the
Incontro-level `Numero di persone`.
