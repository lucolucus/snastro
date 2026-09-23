---
scope: global
status: accepted
supersedes: null
closes_spike: scelta-asr-code-switching
enforced_by: null
---
# 0013 — ASR: Parakeet TDT 0.6B v3 int8 via sherpa-onnx `OfflineRecognizer` (CPU)

## Context
Spike `scelta-asr-code-switching` asked which sherpa-onnx `OfflineRecognizer` model produces the
text of the `Segmento`s for Italian with English words inside sentences, within ADR 0011
(1 h of audio in ≤ 600 s end-to-end on the M3 Pro, models already downloaded). ADR 0004 fixes the
runtime (sherpa-onnx JNI, in-process, confined to `:ml-sherpa`). ADR 0008 (c) fixes the catalogue
entry shape.

Evidence:
- `features/trascrizione-con-parlanti/research/scelta-asr-code-switching.md`. This is desk research
  on the candidates, their licences and asset sizes. It replaced Whisper turbo with Qwen3-ASR 0.6B
  as the main challenger.
- `features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md`. These are the
  measurements: §4 ASR per diarization turn, §5 full-file timing, and "User judgement
  (2026-09-23)".

Measured on 2026-09-23 (M3 Pro, 36 GB, sherpa-onnx 1.13.8, CPU, 6 threads, greedy). All timings
were taken **under heavy load**: parallel Gradle builds pushed load1 from 15 to 156. Read them as
pessimistic upper bounds.

| | Parakeet TDT 0.6B v3 int8 | Qwen3-ASR 0.6B int8 |
|---|---|---|
| ASR RTF, 5-min excerpts E1 / E2 (load 23–31) | **0.028 / 0.024** | 0.099 / 0.107 (about 4× slower) |
| English jargon kept in English spelling (E1 / E2 occurrences) | **11 / 7** | 6 / 5 (often Italianised) |
| Non-Latin script on short turns | **never** | 3/34 (E1), 7/53 (E2): Chinese, once Cyrillic |
| Casing / punctuation | cased and punctuated | often all lowercase, no punctuation |
| Token timestamps in sherpa | yes (`getTokens/getTimestamps/getDurations`) | none |

Full-file run, Via Roquel (4511 s): diarization + Parakeet, one process, model loads included.
- Total time: **578.5 s, RTF 0.128**. Diarization took 244.7 s (see ADR 0014) and ASR took 332.3 s
  (RTF 0.074). Load1 ran 15 → 79.
- Extrapolated to 60 min: **≈ 462 s ≤ 600 s**. The figure excludes alignment and persistence.
- Peak RSS **≈ 2.15 GB**.
- The 10-thread run was under even heavier load (load1 76–156). It is byte-identical in output and
  inconclusive on speed.

**User judgement [user], 2026-09-23.** The user read the full Via Roquel Parakeet transcript and
judged it **good, and the best of the runs**.

## Decision
- **Model:** NVIDIA **Parakeet TDT 0.6B v3**, int8 ONNX export by k2-fsa.
  - Runs through sherpa-onnx `OfflineRecognizer` with the NeMo transducer config
    (`OfflineTransducerModelConfig`, model type `nemo_transducer`).
  - Decoding `greedy_search`. Execution provider **CPU** (ADR 0004/0011). CoreML is not needed:
    the budget is met on CPU.
  - Threads = performance cores. 6 on the M3 Pro was the measured setting.
  - sherpa-onnx **≥ 1.13.8** (the measured version). The JNI release and its natives are pinned by
    the `packaging-modelli-desktop` ADR.
- **Strategy M.** One multilingual auto-detect model per chunk, with no language routing and no
  language hint: Parakeet exposes none, and `Riconoscimento` carries no language.
- **Timestamps.** `Riconoscimento.token` is **filled**, from `getTokens/getTimestamps/getDurations`,
  relative to the chunk (port `tec-riconoscitore`). The sanity of the token timestamps is not yet
  checked. That is input for `allineamento-parole-voci`.
- **Measured chunking.** This is the configuration the numbers above were taken with. It is *not* a
  decision of this ADR:
  1. Diarization turns: same-speaker segments with a gap < 1 s are merged.
  2. Turns > 25 s are split by Silero VAD (threshold 0.5, min silence 0.25 s, max speech 25 s).
  3. Chunks < 0.2 s are dropped.

  The final chunking and turn-merging strategy belongs to the `allineamento-parole-voci` ADR, which
  may change these values within the Parakeet constraint (long chunks cost memory, since the model
  uses full attention).

### `:modelli` catalogue entries (ADR 0008 (c))
| field | ASR |
|---|---|
| `id` | `asr-parakeet-tdt-0.6b-v3-int8` (a new id on any SHA change) |
| `ruolo` | riconoscimento |
| `url` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2` |
| `formato` | `TAR_BZ2` |
| `sha256` (asset) | `5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf`. Computed locally on 2026-09-23; k2-fsa publishes none. |
| `dimensioneByte` (asset) | `487170055` |
| files used inside `percorso(id)` | `encoder.int8.onnx` (652,184,281 B), `decoder.int8.onnx` (11,845,275 B), `joiner.int8.onnx` (6,355,277 B), `tokens.txt` (93,939 B). `test_wavs/` is ignored. |
| `licenza` | CC-BY-4.0 |
| `attribuzione` | "NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx" |

The Silero VAD used in the measured chunking is a separate entry, owned by block `vad-silero`. It is
recorded here because this is where it was measured. The id is a proposal; the `vad-silero` block
may confirm it.

| field | VAD |
|---|---|
| `id` | `vad-silero` |
| `ruolo` | vad |
| `url` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx` |
| `formato` | `FILE` |
| `sha256` | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` |
| `dimensioneByte` | `643854` |
| `licenza` | MIT |
| `attribuzione` | "Silero VAD (MIT), snakers4/silero-vad" |

### Rejected / not measured
- **Qwen3-ASR 0.6B int8.** Measured as the challenger (`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2`,
  Apache-2.0, 878,702,423 B). Rejected because it is about 4× slower, writes foreign scripts
  (Chinese, Cyrillic) into an Italian transcript on short turns, outputs lowercase text with little
  punctuation, Italianises English jargon more often, and gives no token timestamps in sherpa.
- **Whisper large-v3-turbo ONNX. NOT measured.** The spike's closure criterion named it as the
  second candidate. It was not needed: Parakeet meets both the quality bar (user judgement) and
  the budget (ADR 0011). The research also expected Whisper turbo to exceed the budget on CPU
  (≈ 8–16 min per hour, [hypothesis]), and its published exports give no token timestamps. The
  spike clause "if only Whisper is acceptable and misses the budget → back to the user" did not
  trigger. This is a deliberate deviation from the closure criterion, accepted by the user.
- **Cohere Transcribe, Canary-1B-v2, Omnilingual.** Excluded in the research (§2) and not measured.

## Consequences
- **Budget share.** ASR ≈ 0.074 RTF under load, about 332 s of the 462 s extrapolated per hour.
  Diarization plus ASR leave about 23 % headroom *under load*. Idle-machine figures are a
  [hypothesis] (plausibly < 300 s) until block `benchmark-elaborazione` (ADR 0011) runs them.
- **Memory.** About 2.15 GB peak for the whole pipeline in one process (ADR 0004's 1–2 GB
  off-heap estimate, plus the audio in RAM).
- **Packaging.** INT8 speed on Apple Silicon needs an arm64 onnxruntime slice. The pip wheel's ORT
  1.28.2 is arm64-only, and whether the JNI jar's ORT is also arm64-only is unchecked. That check
  goes to `packaging-modelli-desktop`.
- **Accepted known failure modes (v1, no text editing).**
  - **Empty output on short or unclear turns.** 82 of 962 turns on the full file; 4/34 and 7/53 on
    the excerpts.
  - **Short turns (< 2 s) decoded as English-sounding fragments.** Upper bound: 80 of 436 short
    turns on the full file. Most are interjections, crosstalk or background music under speech
    (see ADR 0014 on music).
  - Parakeet has no language hint, so these cannot be steered.
  - How the pipeline treats them (drop empty chunks, merge turns < 1–2 s into neighbours before
    ASR) is decided by `allineamento-parole-voci`. This ADR records them as inputs.
- **Residual gaps (the closure is on user acceptance of the evidence, not on the full criterion)
  [user].**
  1. The spike asked for ≥ 3 real samples. Only **2** recordings were measured, both
     Italian-dominant, with English only as jargon inside Italian sentences. There were **no real
     English turns**, so "wrong-language chunks" and "translated insertions" were barely exercised.
  2. **No WER and no scored switched-phrase list against a reference.** Quality rests on the
     agent's side-by-side read and the user's judgement.
  3. The timings were taken under heavy load, and threads 4 / 8 / 10 were not compared on an idle
     machine.
- **Re-measure trigger.** When a 3rd recording is available, ideally one with real English turns
  or an English speaker, re-run the ASR comparison on it and record the result as an amendment.
  **Reopen** this ADR, via a superseding ADR after measuring Qwen3-ASR with its per-stream `it`/`en`
  hint and Whisper turbo, in either case:
  - real English turns come out systematically wrong;
  - real use shows systematic wrong-language chunks on Italian speech.
- **Discursive.** No grep can check a model choice. The `riconoscitore-sherpa` block's catalogue
  AC and its `@modelli` contract test are the guardians, together with the ADR 0011 benchmark.
