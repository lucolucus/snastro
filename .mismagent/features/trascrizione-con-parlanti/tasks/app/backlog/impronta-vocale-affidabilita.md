---
id: impronta-vocale-affidabilita-spike
type: spike
side: app
repo: .
depends_on: []
---
# Spike / Is ImprontaVocale similarity reliable enough to produce useful Fasce?

> Re-scoped by the architect on 2026-09-23 (ADR 0004): embeddings from sherpa-onnx
> `SpeakerEmbeddingExtractor`; cosine similarity + `SoglieFascia` computed in pure Kotlin
> (`ConfrontoImpronte`).

## Question to answer
On the user's REAL recordings (`sample/`), does `ImprontaVocale` similarity separate same-person
from different-person across devices/rooms/sessions well enough to produce useful `Fascia`s
(`forte | debole | nessuna`)? Which embedding model — **first test reusing the diarizer's extractor**
(one model for both jobs), then the alternatives from sherpa's catalogue, VoxCeleb/English-trained
(language-independent) preferred over zh-cn variants:
- 3D-Speaker ERes2Net / ERes2NetV2 / CAM++ (VoxCeleb);
- WeSpeaker ResNet34 / ECAPA (VoxCeleb);
- NeMo TitaNet-small / large.

## Closure criterion
A same/different-person similarity distribution measured on ≥ 3 real `Registrazione`s with ≥ 2
recurring people; `SoglieFascia` (forte/debole) calibrated and written down (ADR); the false
"forte" rate on different people reported, with an explicit go/no-go for automatic `Proposta`s;
extraction time per hour counted in the NFR budget (ADR 0011). The embedding-model choice (and its
catalogue entry: URL + SHA-256 + licence) is recorded in the same ADR.

## Unblocks
(block ids pinned by build-manifest) Parlanti: `proposta` read-model ([INV-20]), the
`EstrattoreImpronta` adapter in `:ml-sherpa` and `ConfrontoImpronte` used by
`conferma-attribuzione` / `salta-voce` / revisione-policy application-services ([INV-15], [INV-21]).
