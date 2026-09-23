---
id: scelta-diarizzatore-spike
type: spike
side: app
repo: .
depends_on: []
status: answered
closed_by: 0014-diarizzazione-sherpa-numero-persone
closed: 2026-09-23 [user]
---
# Spike / Which sherpa-onnx diarization configuration produces the Voci?

> Re-scoped by the architect on 2026-09-23 (ADR 0001/0004: all-Kotlin, sherpa-onnx in-process).
> pyannote community-1 and NVIDIA Sortformer are **dropped**: both are Python-only and cannot run
> in sherpa-onnx.

## Question to answer
Which configuration of sherpa-onnx `OfflineSpeakerDiarization` produces the `Voce`s of a
`Trascritto` for IT/EN work meetings with 2–4 speakers, within the NFR budget (ADR 0011)?
- **Segmentation:** pyannote segmentation-3.0 ONNX (MIT, sherpa release, no HF gate) vs
  reverb-diarization-v1 ONNX (check its licence first — possibly non-commercial; drop if so).
- **Embedding extractor** (shared with spike `impronta-vocale-affidabilita`): 3D-Speaker
  ERes2Net / ERes2NetV2 / CAM++ (VoxCeleb variants), WeSpeaker ResNet34 / ECAPA (VoxCeleb), NeMo
  TitaNet-small/large.
- **Clustering:** FastClustering with a tuned threshold (default) vs a speaker-count hint (would
  need a UI field — only as an option to report).
- Execution provider: CPU; CoreML measured only as a comparison.

## Closure criterion
On the same real samples in `sample/` (≥ 3 `Registrazione`s): error estimate (DER if a reference
is annotated, otherwise a visual count of wrong turns per 10 min), wall-clock per hour of audio on
the M3 Pro (CPU, and CoreML if tried), licence of each model; one configuration chosen in an ADR
that adds it to the `:modelli` catalogue (URL + SHA-256 + licence).
**Fallback if no configuration is good enough:** tune threshold/embedding → rely on `Revisione` →
(later only, superseding ADR) an out-of-process Python pyannote-community-1 adapter behind the same
`Diarizzatore` port.

## Unblocks
(block ids pinned by build-manifest) Trascrizione: `Diarizzatore` adapter in `:ml-sherpa` behind
`avvia-elaborazione`; spikes `allineamento-parole-voci`, `impronta-vocale-affidabilita` (embedding
reuse).

## Closure (2026-09-23) [user]
Answered by ADR [0014-diarizzazione-sherpa-numero-persone](../../../../../decisions/0014-diarizzazione-sherpa-numero-persone.md). pyannote segmentation-3.0 int8 + WeSpeaker ResNet34-LM, FastClustering threshold 0.4, windowShiftRatio 0.5; NEW optional "Numero di persone" → `num_clusters` (auto threshold clustering otherwise). Music filtering (CED) and stricter VAD rejected.
Closed on **user acceptance of the evidence**
(`features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md`), NOT on the full
criterion. Residual gaps: 2 real recordings instead of ≥ 3 (both Italian with English jargon, no real
English turns); no DER against reference labels (quality judged by the user); timings under heavy
load. Re-measure when a 3rd recording (ideally with English turns) is available — see the ADR's
re-measure trigger.
