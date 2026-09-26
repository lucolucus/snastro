---
id: filtro-fuori-tema-spike
type: spike
side: app
repo: .
depends_on: []
---
# Spike / Does the Argomento leave out what is fuori tema without losing on-topic Decisioni?

> Source: context-map "Open spikes" (added 2026-09-25, feature `sintesi`). Not measured by the model
> spike (`research/scelta-modello-llm.md`). Can run on the Ollama bench, like the model spike.

## Question to answer
Given a short `Argomento`, does Qwen3.5 9B leave out what is `fuori tema` WITHOUT losing on-topic
`Decisione`s / `Azione`s — and with no `Argomento`, does it summarize everything? What is the
`Argomento` length bound (provisional 200 characters, tactical [INV-S6])?

## Closure criterion
On ≥ 2 real recordings that contain off-topic talk, each is summarized with and without an
`Argomento`; the user judges that off-topic content is left out and that no on-topic
`Decisione`/`Azione` is lost (count reported); the prompt wording and the `Argomento` length bound
are recorded (ADR, or amendment of the runtime ADR).

## Unblocks
(block ids pinned by build-manifest) the prompt of the `ModelloLinguistico` adapter / `esegui-riassunto`
input builder, and the `Argomento` VO bound (riassunto aggregate) + `ArgomentoTroppoLungo` in
`riassumi`. The blocks can be built on the provisional bound and wording; the spike fixes them.
