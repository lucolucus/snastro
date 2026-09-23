# Misure R1 — ASR code-switching + diarizzazione (spike `scelta-asr-code-switching`, `scelta-diarizzatore`)

Date: 2026-09-23 · Machine: Apple M3 Pro, 36 GB, macOS (Darwin 25.4) · User-approved experiment.
Everything was run **outside the repo** (scratch dir, Python venv). No audio and no transcript text
is in this file: it holds offsets, counts and timings only. The transcripts stay in the scratch dir
for the user to judge.

**Load warning.** Parallel Gradle builds were running during the whole session. Load average
(1 min) ranged **15 → 156**. Every timing below carries the load recorded next to it. Treat all
absolute times as **pessimistic upper bounds**, not as the NFR measurement. The contention ran
highest during the full-file runs.

## 1. Setup

| Item | Value |
|---|---|
| sherpa-onnx | **1.13.8** (pip `sherpa-onnx` + `sherpa-onnx-core` 1.13.8, Python 3.14.6 venv) |
| ONNX Runtime bundled | `libonnxruntime.dylib` **1.28.2**, `file`: *Mach-O 64-bit … arm64* (**arm64-native, not universal2**), 29.0 MB |
| Other native libs | `libsherpa-onnx-c-api.dylib`, `-cxx-api.dylib`, `_sherpa_onnx…darwin.so` — all arm64 |
| Qwen3-ASR in this version | **yes** (`OfflineRecognizer.from_qwen3_asr`) |
| `window_shift_ratio` exposed in Python | **yes** (`OfflineSpeakerSegmentationPyannoteModelConfig(model, window_shift_ratio)`) |
| Audio conversion | `afconvert -f WAVE -d LEI16@16000 -c 1` (no ffmpeg); excerpts cut with Python `wave` |

### Downloaded assets (sizes + SHA-256 computed locally)

| Asset | Download size | Extracted | SHA-256 (downloaded bytes) | Note |
|---|---|---|---|---|
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2` | 487,170,055 B | 643 MB (encoder.int8 652,184,281 B; decoder.int8 11,845,275 B; joiner.int8 6,355,277 B; tokens.txt 93,939 B) | `5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf` | research had no hash |
| `sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2` | 878,702,423 B | 972 MB (conv_frontend 44,148,281 B; **encoder.int8 182,491,662 B; decoder.int8 755,914,231 B**; tokenizer/) | `393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96` | **corrects research §2**: archive is ~0.88 GB (not 1.95 GB); the decoder is the big file (the research had encoder/decoder sizes swapped) |
| `sherpa-onnx-pyannote-segmentation-3-0.tar.bz2` | 6,958,444 B | 7.2 MB (model.onnx 5,992,913 B; model.int8.onnx 1,540,506 B) | `24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488` | not published upstream, pin this |
| `wespeaker_en_voxceleb_resnet34_LM.onnx` | 26,530,550 B | — | `e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012` | **matches** release checksum.txt |
| `silero_vad.onnx` (asr-models release) | 643,854 B | — | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | |

Diarization used segmentation **`model.int8.onnx`** throughout (the fp32 `model.onnx` was not compared: that is still open).

## 2. Inputs and excerpts (offsets only)

| Recording | Duration (16 kHz mono WAV) |
|---|---|
| `New Recording 4.m4a` | 1135.3 s (18 min 55 s) |
| `Via Roquel.m4a` | 4511.3 s (75 min 11 s) |

**Language probing.** I ran Qwen3 on 1-min probes, every 120 s in NR4 and every 300 s in Via
Roquel, and counted Italian vs English function words. **Both recordings are Italian-dominant
everywhere.** English shows up as **domain terms inside Italian sentences** (game-design jargon),
not as English turns. No probe was English-dominant. I chose the windows around the probes with
the most English tokens:

| Excerpt | Recording | Offset | Length |
|---|---|---|---|
| E1 `E-nr4-0` | New Recording 4 | 0 s – 300 s | 300 s |
| E2 `E-roquel-2640` | Via Roquel | 2640 s – 2940 s (44:00–49:00) | 300 s |

## 3. Diarization sweep (sherpa `OfflineSpeakerDiarization`)

Settings: segmentation pyannote-3.0 int8 + WeSpeaker ResNet34-LM, `num_clusters=-1`,
`min_duration_on=0.3`, `min_duration_off=0.5`, `num_threads=6` (segmentation and embedding).
"Speakers" = clusters found. "Tiny" = clusters with < 10 s total in the 300-s excerpt. Each run
also gives seconds per speaker, largest first. The time excludes model load (≈ 0.03 s).

**Semantics fact (important for the ADR):** in sherpa `FastClustering`, `threshold` is a
**distance cut-off**. A *higher* threshold gives *fewer* speakers. The requested grid
{0.5, 0.7, 0.9} collapsed to 1 speaker almost everywhere, so I extended the sweep downward to
0.2–0.6.

### E1 (NR4 0–300 s)

| wsr | th | speakers (tiny) | seconds/speaker | proc s | RTF | load1 |
|---|---|---|---|---|---|---|
| 0.1 | 0.2 | 10 (5) | 130,53,13,12,11,7,1,1,0,0 | 45.7 | 0.152 | 66 |
| 0.1 | 0.3 | 5 (2) | 143,64,14,7,0 | 44.3 | 0.148 | 65 |
| 0.1 | 0.4 | 2 (0) | 215,13 | 33.2 | 0.111 | 70 |
| 0.1 | 0.45 | 2 (0) | 215,13 | 34.6 | 0.116 | 50 |
| 0.1 | 0.5 | 2 (0) | 215,13 | 26.3 | 0.088 | 21 |
| 0.1 | 0.55 | 2 (0) | 215,13 | 53.8 | 0.179 | 36 |
| 0.1 | 0.6 / 0.7 / 0.9 | 1 | 221 | 27.8 / 27.3 / 36.7 | 0.09–0.12 | 63 / 17 / 16 |
| 0.5 | 0.2 | 5 (1) | 115,90,26,11,2 | 5.8 | 0.019 | 48 |
| 0.5 | 0.3 | 2 (0) | 218,28 | 5.3 | 0.018 | 42 |
| **0.5** | **0.4** | **2 (0)** | 218,28 | **6.6** | **0.022** | 40 |
| 0.5 | 0.45 | 2 (0) | 218,28 | 6.4 | 0.021 | 38 |
| 0.5 | 0.5 … 0.9 | 1 | 240 | 6.2–9.0 | 0.02–0.03 | 24–36 |

### E2 (Via Roquel 44:00–49:00)

| wsr | th | speakers (tiny) | seconds/speaker | proc s | RTF | load1 |
|---|---|---|---|---|---|---|
| 0.1 | 0.2 | 15 (10) | 51,39,35,25,22,10,… | 27.5 | 0.092 | 37 |
| 0.1 | 0.3 | 8 (4) | 69,67,39,23,4,2,1,0 | 46.7 | 0.156 | 29 |
| 0.1 | 0.4 | 4 (1) | 102,71,26,8 | 36.7 | 0.122 | 45 |
| 0.1 | 0.45 / 0.5 / 0.55 | 3 (1) | 172,25,9 | 44.4 / 45.0 / 45.5 | ~0.15 | 35 / 28 / 30 |
| 0.1 | 0.6 | 2 (0) | 172,35 | 43.5 | 0.145 | 25 |
| 0.1 | 0.7 / 0.9 | 1 | 203 | 35.5 / 42.7 | 0.12–0.14 | 66 / 64 |
| 0.5 | 0.2 | 8 (3) | 65,49,44,37,26,4,4,2 | 9.8 | 0.033 | 27 |
| 0.5 | 0.3 | 5 (1) | 107,49,39,33,2 | 9.8 | 0.033 | 26 |
| **0.5** | **0.4** | **4 (1)** | 154,39,33,2 | **8.9** | **0.030** | 26 |
| 0.5 | 0.45 / 0.5 / 0.55 | 2 (0) | 190,38 | 11.7 / 8.5 / 10.4 | 0.03–0.04 | 26 / 75 / 30 |
| 0.5 | 0.6 … 0.9 | 1 | 219 | 6.5–9.9 | 0.02–0.03 | 32–75 |

**Reading.** No reference labels exist yet, so these are speaker *counts*, not DER. I picked
**wsr = 0.5, threshold = 0.4** as "most plausible" for three reasons:
- the count is stable across 0.3–0.45 on E1;
- it gives the largest count without a swarm of tiny clusters on E2;
- it follows the research's "prefer slight over-count" rule.

On the E2 transcript, the 3 main voices change at plausible turn boundaries. The 4th cluster is
2 s of interjections. Other observations:
- **wsr 0.5 is ~4–5× faster than 0.1** on the same load.
- At the same threshold, wsr 0.5 tends to find *more* separation than 0.1 on E2 (4 vs 4 at 0.4;
  2 vs 3 at 0.45–0.55), so its quality is **not obviously worse**. That needs DER to confirm.
- Only ~220–240 s of each 300-s excerpt is labelled as speech.

## 4. ASR per diarization turn (excerpts)

Pipeline:
1. Take diarization segments (wsr 0.5 / th 0.4).
2. Merge same-speaker segments with a gap < 1 s into a turn.
3. Split turns > 25 s with Silero VAD (threshold 0.5, min silence 0.25 s, max speech 25 s) into
   ≤ 25 s chunks. Drop chunks < 0.2 s.
4. Run one recognizer call per chunk, `num_threads=6`, greedy.

The time includes VAD and excludes model load.

| Excerpt | Model | turns / chunks | model load s | ASR s | RTF | load1 |
|---|---|---|---|---|---|---|
| E1 | Parakeet TDT v3 int8 | 34 / 34 | 0.9 | **8.5** | **0.028** | 31 |
| E1 | Qwen3-ASR 0.6B int8 | 34 / 34 | 1.5 | 29.7 | 0.099 | 31 |
| E2 | Parakeet TDT v3 int8 | 53 / 63 | 0.9 | **7.3** | **0.024** | 24 |
| E2 | Qwen3-ASR 0.6B int8 | 53 / 63 | 1.5 | 32.2 | 0.107 | 23 |

**Detected language.** The Python result's `lang` field came back **empty for every chunk, for
both models**. Qwen3 does not surface its LID through sherpa 1.13.8 `OfflineRecognizerResult.lang`
in this path. Per-chunk language is therefore not available from either model as used.

### Qualitative observations (no private content quoted)

Counts come from automatic heuristics plus a manual side-by-side read by the agent. They are
**not** a WER, and there is no reference yet.

- **English terms inside Italian sentences.** The recurring English jargon (3–4 distinct terms)
  appears in both excerpts. **Parakeet mostly keeps it in correct English spelling**: 11 correct
  term occurrences in E1 vs 6 for Qwen3, and 7 vs 5 in E2. **Qwen3 more often Italianises it
  phonetically** (misspelled forms, or the term replaced by a similar-sounding Italian word).
- **Hallucinations on short / non-speech turns.**
  - **Qwen3** emitted **non-Latin-script text (Chinese, once Cyrillic) on 3 of 34 turns (E1) and
    7 of 53 turns (E2)**, plus a couple of Portuguese-looking outputs on short E2 turns. It never
    returned empty text. It logged one `decode collapsed into a repetition loop` warning (probe run).
  - **Parakeet never produced a non-Latin script.** Its failure mode on short or unclear turns is
    either **empty output** (4/34 E1, 7/53 E2) or a **short English-sounding phrase**. The English
    phrase happened on short (< 2 s) turns in **1 of 8 (E1) and 4 of 25 (E2)**.
- **Italian long-form quality.** On long turns the two are close, and both have phonetic errors
  on informal/overlapping speech. On my side-by-side read Parakeet's sentences were more often
  intelligible and correctly punctuated/cased. Qwen3 output is often lowercase without
  punctuation.
- **Speed.** Parakeet is **~3.5–4.4× faster** than Qwen3 on the same chunks and load.
- **Timestamps.** Not evaluated in this run: turn timestamps come from diarization. Token
  timestamps from Parakeet are still to check (`allineamento-parole-voci`).

## 5. Full-file timing — Via Roquel (4511.3 s)

Pipeline: read WAV → diarization (wsr 0.5 / th 0.4, int8 seg + ResNet34-LM) → Parakeet over all
turns (VAD split ≤ 25 s). Single Python process under `/usr/bin/time -l`. The timings include
model loads.

| Run | load1 before → after | Diarization s (RTF) | ASR s (RTF) | Total wall s (RTF) | Extrapolated 60 min | Peak RSS | user CPU s |
|---|---|---|---|---|---|---|---|
| threads = 6 | **15 → 44 → 79** | 244.7 (0.054) | 332.3 (0.074) | **578.5 (0.128)** | **≈ 462 s** | **2.16 GB** (footprint 2.14 GB) | 2195 |
| threads = 10 | **76 → 121 → 156** | 274.1 (0.061) | 341.3 (0.076) | 616.8 (0.137) | ≈ 492 s | 2.14 GB | 3656 |

- Outputs were identical across the two runs (deterministic): **10 clusters**.
  - Seconds per cluster: 974, 851, 433, 379, 315, 311, 37, 15, 6, 2. That is **6 substantial
    clusters + 4 small ones**, from 1109 raw segments.
  - The true count is unknown. With 4 clusters on the E2 excerpt, the full-file count probably
    **over-counts**: one real voice likely split across the 75 min. This is expected with a single
    distance threshold. The research prefers over-count because `unire` (merge) is cheaper than
    `dividi` (split).
- ASR stats (full file): 962 turns / 977 chunks.
  - **82 empty turns.**
  - **436 turns shorter than 2 s. 80 of them came out English-dominant** by the function-word
    heuristic. Most are probably interjections or crosstalk, not real Italian sentences lost.
    That is an upper bound on "short Italian turns decoded as English".
- **Against ADR 0011 (600 s for the whole 60-min pipeline):** even under **load 15–79** the
  extrapolation is **≈ 462 s**. That is within budget, but with only ~23 % headroom, and ASR
  alignment/persistence is not included.
- The 10-thread run was under even heavier load (76–156), so it **cannot tell whether 10 threads
  help**. It must be repeated on an idle machine.
- On the excerpts at load ~25–40, Parakeet's RTF was 0.024–0.028 and diarization (wsr 0.5) was
  0.02–0.03. The full run showed ~3× worse ASR RTF under ~2–3× the load. So an idle-machine
  60-min run is plausibly **well under 300 s**, but that is **[hypothesis]** until measured.
- Diarization RTF on the full file (0.054) is ~2× the excerpt RTF. Part of that is load, and part
  may be clustering over ~1100 embeddings. This needs an idle rerun with `debug=true` per-stage
  timing.
- **Memory:** peak RSS ≈ **2.15 GB** for the whole pipeline in one process: WAV in RAM, Parakeet
  loaded, diarization models loaded. That is under the 3 GB ASR bar proposed in the research.

## 6. Provisional read (for the closure ADRs, to be confirmed by the user)

- **ASR:** **Parakeet TDT 0.6B v3 int8** is ahead of Qwen3-ASR 0.6B int8 on these samples. It:
  - is faster (~4×);
  - keeps English jargon better;
  - does not hallucinate foreign scripts on short turns.

  Qwen3's failure mode (Chinese/Cyrillic text in an Italian transcript) is the worse one for
  users. Parakeet's residual issue is short turns turning into empty or English-sounding
  fragments. Candidate mitigations: a minimum-duration filter on turns, or merging turns
  < 1–2 s into neighbours before ASR.
- **Diarization:** pyannote-3.0 int8 + WeSpeaker ResNet34-LM, `wsr = 0.5`, **threshold around
  0.4** (not 0.5–0.9). Counts look plausible on 5-min excerpts. On 75 min it over-counts (10
  clusters, 6 substantial). The threshold is the key knob and must be tuned against real labels.

## 7. Still needed (to close the spikes)

1. **User judgement of the transcripts** in the scratch dir: E1/E2 × Parakeet/Qwen3, plus the full
   Via Roquel Parakeet transcript. Specifically: are the voice assignments right? What is the true
   speaker count in each recording and in the full Via Roquel? Which ASR reads better?
2. **Reference labels** for DER and WER / switched-phrase scoring (research §7 step 2, §8 step 1).
   Without them the threshold choice is a count-plausibility guess.
3. **A 3rd recording** (spike closure needs ≥ 3), ideally with an actual English turn or speaker,
   since both current recordings have only intra-sentence English terms. The research's
   "wrong-language chunks" and "translated insertions" metrics were barely exercised.
4. **Idle-machine rerun** of the full 60–75 min pipeline: threads 4 / 6 / 8 / 10, diarization
   `debug=true` per-stage timing, fp32 vs int8 segmentation, and whether the full-file
   over-count responds to a slightly higher threshold (0.45–0.5) on long files only.
5. Parakeet token-timestamp sanity check (research §7 step 6), and the Java/JNI path
   (`sherpa-onnx-native-lib-osx-aarch64` jar ORT arch; the pip wheel's ORT is arm64-only 1.28.2).
6. Not run: Whisper turbo, CAM++ / TitaNet embeddings, Qwen3 with the `it` hint (the Python
   factory exposes no language parameter, only `hotwords`).

## User judgement (2026-09-23)
- Transcript quality of Parakeet on the full Via Roquel run is judged **good / the best** of the runs (the t6 and t10 files are byte-identical: threads change speed only).
- Via Roquel had **4 real speakers**; the diarizer produced 10 clusters (8 with substantial speech) → over-count confirmed.
- The recording has **background music**; the user reports it was picked up too — i.e. music passages are transcribed as speech and very likely split into extra "voices" (the short English fragments seen in the excerpt are consistent with sung lyrics).
- Open for R1: music/non-speech rejection before ASR+diarization, and speaker-count control (known number of speakers, or merge of small/near clusters).

## Follow-up: speaker count and music (2026-09-23, full Via Roquel, 4511 s, ≤6 threads)
| variant | clusters | speech s per cluster | diar wall |
|---|---|---|---|
| baseline auto (th 0.4, wsr 0.5) | 10 | 974, 851, 433, 379, 315, 311, 37, 15, 6, 2 | — |
| A: num_clusters=4 | 4 | 1874, 737, 367, 349 | 67 s |
| B: music removed + auto | 12 | 1327 … 2 | 68 s |
| B + merge <3 % into nearest | 5 | 1378, 771, 446, 337, 221 | — |
| B: music removed + num_clusters=4 (v2 transcript) | 4 | 2118, 664, 323, 53 | 70 s + 6 s tagging |
| C: Silero VAD 0.6 + auto | 14 | 973 … 3 | 66 s + 12 s |
- Music tagger: sherpa-onnx CED-mini audio tagging; flagged 156 s (3.5 %), mostly sub-2 s blips inside speech turns — background music under talk is NOT separable this way; only one tiny phantom cluster was mostly music.
- Auto clustering over-splits even without music; a known speaker count (num_clusters) is the only variant that gives 4. Stricter VAD makes it worse.
- Candidate for the app: optional "numero di persone" at import (num_clusters), auto otherwise. Pending user listening check of A vs B-nc4 balance (B-nc4 has a 53 s cluster — possibly a real speaker merged away).
