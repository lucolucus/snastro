---
id: scelta-asr-code-switching-spike
type: spike
side: app
repo: .
depends_on: []
---
# Spike / Which ASR for IT+EN code-switching: Parakeet v3 int8 vs Whisper large-v3-turbo (ONNX, sherpa-onnx)?

> Re-scoped by the architect on 2026-09-23 (ADR 0004): candidates are sherpa-onnx ONNX exports
> only; MLX variants are dropped (Apple-only, not sherpa).

## Question to answer
Which sherpa-onnx `OfflineRecognizer` model produces the text of the `Segmento`s for Italian +
English with in-sentence code-switching, **within the NFR** (ADR 0011: 1 h of audio in ≤ 10 min
end-to-end on the M3 Pro, models downloaded)?
- **Parakeet TDT 0.6B v3 int8** (NeMo transducer, 25 European languages incl. IT/EN, automatic
  language detection, token timestamps, very fast on CPU). Risk: English words inside an Italian
  sentence mangled.
- **Whisper large-v3-turbo ONNX int8** (sherpa; needs VAD chunks ≤ 30 s, no reliable word
  timestamps; slower — CPU and CoreML execution provider both measured).

## Closure criterion
Both run on real mixed IT/EN samples from `sample/`; recorded: qualitative accuracy on switched
sentences (a short scored list of switched phrases), timestamp availability, wall-clock per hour on
the M3 Pro (CPU; CoreML for Whisper), and the **total pipeline time vs the 600 s budget** together
with the chosen diarization configuration. One model chosen in an ADR (adds it to the `:modelli`
catalogue). If only Whisper is acceptable and it misses the budget, the trade-off (quality vs NFR)
goes back to the user.

## Unblocks
(block ids pinned by build-manifest) Trascrizione: `RiconoscitoreParlato` adapter in `:ml-sherpa`
behind `avvia-elaborazione`; spike `allineamento-parole-voci` (strategy B needs token timestamps).
