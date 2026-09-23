# Research — scelta-asr-code-switching (IT+EN ASR on sherpa-onnx, CPU)

- **Feature:** trascrizione-con-parlanti · **Spike:** `tasks/app/backlog/scelta-asr-code-switching.md`
- **Consumer (FEEDS):** the ADR that closes the spike (adds the ASR entry to the `:modelli` catalogue,
  gates block `riconoscitore-sherpa`); spike `allineamento-parole-voci` (needs to know whether
  `Riconoscimento.token` can be non-null); `benchmark-elaborazione` (ADR 0011 time share).
- **Date:** 2026-09-23 · **Status of the spike after this file:** *advanced, NOT closed.* The desk
  research is done; the closure criterion requires running the models on the real `sample/`
  recordings, which this research pass could not do (no execution). Section 7 is the exact run
  protocol that closes it.

Legend: **[F]** = fact from a cited source · **[H]** = hypothesis / estimate, to be measured.

---

## 1. Question and constraints

Which sherpa-onnx `OfflineRecognizer` model turns the audio of meetings (2–4 speakers, Italian and
English mixed within the same meeting and within sentences) into text, locally on CPU, within
ADR 0011 (60-min real sample, whole `Elaborazione` ≤ 600 s on the M3 Pro 36 GB, models already
downloaded)?

Constraints from the trunk:
- ADR 0004: sherpa-onnx Java/JNI in-process, CPU default, CoreML only if a spike shows it helps.
- ADR 0008 (+ amendment c): catalogue entry = one asset on the k2-fsa GitHub releases
  (`TAR_BZ2` or `FILE`), pinned SHA-256 of the asset, size, licence + attribution; no HF token.
- Product brief: text editing is **out of scope in v1** → ASR errors are permanent in the `.md`;
  accuracy on the mixed-language speech matters more than usual.
- Port pinned by the manifest: `Riconoscimento(testo, token: List<Token>?)` — `token` null if the
  model gives no timestamps.

Two different kinds of code-switching occur in Italian work meetings, and they stress models
differently **[H]**:
1. **Intra-sentence insertions** — English terms inside Italian sentences ("facciamo il deploy
   dopo la call", "il budget del Q3"). A model that picks ONE language per chunk and forces it
   tends to Italianise or translate the English words.
2. **Inter-turn switching** — a guest or a whole exchange in English, then back to Italian.
   Chunk-level language detection handles this if chunks follow speaker turns.

## 2. Candidates surveyed (everything sherpa-onnx ships that could cover IT + EN)

| Model (sherpa asset) | IT+EN | Language handling | Timestamps in sherpa | Licence (weights) | Asset size | CPU speed evidence | Verdict |
|---|---|---|---|---|---|---|---|
| **Parakeet TDT 0.6B v3 int8** — `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2` | yes (25 EU langs) | auto, **no hint parameter**; `lang` field empty | **token timestamps + durations** [F] | CC-BY-4.0 [F] | ~640–671 MB (encoder.int8 622–652 MB, decoder 12 MB, joiner 6 MB, tokens 92 KB) [F] | fastest of the set (see §4) | **primary candidate** |
| **Qwen3-ASR 0.6B int8** — `sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2` | yes (30 langs + dialects) | auto LID (98 % on FLEURS/CV) + optional per-stream language hint; vendor claims mixed-language support | **none in sherpa** (Qwen timestamps need the separate Qwen3-ForcedAligner, not in sherpa) [F] | Apache-2.0 [F] | archive reported ~1.95 GB, extracted ~937 MB (conv_frontend 42 MB, encoder.int8 721 MB, decoder.int8 174 MB, tokenizer/) [F — the archive/extracted mismatch is unexplained, verify] | RTF 0.103 (EN, 334 s) with 2 threads, sherpa docs [F] | **challenger for code-switching** (new; not in the spike node) |
| **Whisper large-v3-turbo int8** — `sherpa-onnx-whisper-turbo.tar.bz2` | yes (99 langs) | per 30-s call: forced (`language`, Java default **"en"**) or auto-detect; one language per call | segment + token timestamps since sherpa-onnx **1.12.24** (PR #2945, merged 2026-02-05), but **token timestamps need an attention-enabled re-export**; the published `csukuangfj/sherpa-onnx-whisper-*` models do not have it [F]; Java exposure unclear [H] | MIT | HF repo holds int8 (encoder 675 MB + decoder 361 MB ≈ 1.04 GB) **and** fp32 (≈ 3.2 GB); the GitHub tar.bz2 size is unverified — if it bundles fp32 too it is ~4 GB to download [H] | 22 s audio in 5.98 s on a 13th-gen i5, int8, CPU (RTF ≈ 0.27) [F, third-party] | reference for quality; **likely over the NFR on CPU** |
| Cohere Transcribe (2B) int8 — `sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01.tar.bz2` | yes (14 langs) | **language must be set**; model card: "no automatic language detection … inconsistent performance on code-switched audio" [F] | none [F] | Apache-2.0 [F] | ~2.65 GB (encoder.int8.onnx.data 2.5 GB + decoder 146 MB) [F] | RTF 0.104 (EN, 2 threads) [F] | reject (needs per-segment LID; bad on intra-sentence switching; biggest download) |
| Canary-1B-v2 (25 EU langs) | yes | language must be set | segment-level only upstream | CC-BY-4.0 | — | — | **not usable yet**: sherpa honoured only en/es/de/fr until PR #3962 (merged 2026-09-20, after release 1.13.8); no k2-fsa export published [F] |
| Canary-180M-flash | **no IT** (en/es/de/fr) [F] | | | | | | excluded |
| Omnilingual ASR CTC 300M / 1B | IT among 1600+ langs | CTC, char-level | timestamps [F] | (LICENSE in archive; not verified) | 300M int8 ~348 MB [F] | RTF 0.225 (short clip) [F] | excluded: no punctuation/casing, much weaker on high-resource langs [H] |
| SenseVoice | **no IT** (zh/yue/en/ja/ko) [F] | | | | | | excluded |
| Moonshine v1/v2 | **no IT** (v2: ar/zh/en/ja/ko/es/uk/vi) [F] | | | | | | excluded |
| Zipformer/transducer icefall models | no Italian model published [F — none found] | | | | | | excluded |

sherpa Java API status (master): `OfflineTransducerModelConfig`, `OfflineWhisperModelConfig`
(fields `language` default `"en"`, `task`, `tailPaddings`, `enableTokenTimestamps`,
`enableSegmentTimestamps`), `OfflineQwen3AsrModelConfig` (`maxTotalLen` 512, `maxNewTokens` 128,
`hotwords`, sampling params), `OfflineCohereTranscribeModelConfig`, `OfflineCanaryModelConfig`,
`SpokenLanguageIdentification` all exist; `OfflineRecognizerResult` exposes `getText`,
`getTokens`, `getTimestamps`, `getDurations`, `getLang` [F]. Latest release **v1.13.8
(2026-09-10)** ships `sherpa-onnx-jvm-1.13.8.jar` + per-OS `sherpa-onnx-native-lib-<os>-<arch>`
jars incl. `osx-aarch64`, `osx-x64`, `win-x64`, `linux-x64` [F] (relevant to spike
`packaging-modelli-desktop`).

## 3. Accuracy evidence (published, NOT code-switched)

- Parakeet v3 model card: Italian WER FLEURS 3.00, MLS 10.08, CoVoST 3.69; English LS test-clean
  1.93 [F]. The NVIDIA paper (arXiv 2509.14128) reports Italian roughly on par with
  Whisper-large-v3 (FLEURS/CoVoST2/MLS within ~1 point either way) [F — values read from the
  paper's tables/figures by a summariser; treat as indicative].
- Qwen3-ASR technical report (arXiv 2601.21337): Italian FLEURS 0.6B 4.99 / 1.7B 2.41 /
  Whisper-large-v3 4.99; CommonVoice-it 0.6B 10.16; MLS-it 0.6B 19.21 [F]. The HF card quotes
  FLEURS-it 2.88 for the 0.6B [F] — the two sources disagree; normalisations differ. Either way the
  **0.6B is not better than Parakeet v3 on monolingual Italian** and is clearly worse on MLS-it.
- **Code-switching: no published measurement exists for any candidate on IT+EN.** Parakeet v3's
  card/paper say nothing about it [F]; the "Whisper is safer for heavy code-switching" claims found
  are opinion, not measurement [F: Spokenly blog]. Cohere explicitly disclaims it [F]. Qwen claims
  "cross-lingual" handling; its report evaluates it only for the forced aligner [F].
- Known Parakeet v3 failure mode: auto-detection **biased to English**, users of a macOS dictation
  app got English output for French speech (FluidAudio issue #303) [F]. Short chunks (backchannels,
  1–2 s turns) are the likely trigger [H]. sherpa offers no language hint for Parakeet [F: `lang`
  empty, no config field].
- Known Whisper failure modes (general knowledge, [H] for this corpus): forcing the wrong language
  makes it *translate* instead of transcribe; hallucinations on silence/noise → must be fed VAD
  speech only.

## 4. Speed vs ADR 0011 (600 s for the whole pipeline)

- **Critical platform finding [F]:** sherpa-onnx macOS archives bundled a *universal2*
  libonnxruntime whose arm64 slice runs INT8 models **~3× slower** than the arm64-only build of the
  same ORT (1.27.0). Measured on Apple M5, 11 s clip: parakeet-tdt-0.6b-v3 665 ms → 230 ms after
  switching to the arm64-only ORT (OpenWhispr PR #2116). Cohere/Nemotron affected too. Whether the
  `sherpa-onnx-native-lib-osx-aarch64` JNI jar carries an arm64-only ORT is **unknown** → must be
  checked by `packaging-modelli-desktop`; it can decide the NFR on its own.
- Parakeet v3 int8: M5 CPU RTF ≈ 0.021 with the arm64 ORT [F]; sherpa docs RTF 0.325 on a 3.8 s
  clip, 2 threads, unknown host (short clips are dominated by overhead) [F]. **Estimate for 60 min
  on the M3 Pro, 6 threads: ~2–4 min** (≈ 6–12 min with the slow universal2 ORT) [H].
- Qwen3-ASR 0.6B int8: RTF ≈ 0.08–0.10 with 2 threads (host not stated) [F]. **Estimate ~3–6 min
  per hour** on 6 P-cores [H]; autoregressive → cost grows with speech density.
- Whisper turbo int8: RTF ≈ 0.27 on an i5-13th [F]. **Estimate ~8–16 min per hour on CPU** [H] →
  alone it consumes or exceeds the 600 s before diarization. CoreML EP could help the encoder but
  the autoregressive decoder dominates on dense speech [H]; ADR 0004 keeps CoreML opt-in only.
- Diarization (segmentation + embeddings) is the other big share (ADR 0011); an ASR phase above
  ~300 s leaves too little for it [H — the diarization spike will size it].

## 5. Strategy: per-segment language routing vs one multilingual model

**Option R — VAD/diarization segment → spoken-language ID (sherpa `SpokenLanguageIdentification`,
Whisper tiny/base/small/medium encoders) → forced-language model.** Only meaningful for models that
*need* a language (Cohere, Canary, Whisper forced). Drawbacks: an extra model and pass; LID on
short turns is unreliable [H]; a single label per segment **cannot fix intra-sentence
insertions** (kind 1 above) — the forced model will mangle/translate the English words; more
weights to download. Benefit: fixes inter-turn switching for forced-language models.

**Option M — one multilingual auto-detect model per chunk.** Parakeet v3 (no hint possible),
Qwen3-ASR (auto, hint optional), Whisper (auto per 30-s call). Chunk = diarization turn split by
Silero VAD to ≤ ~20–30 s (Whisper hard limit 30 s; Qwen3 `maxNewTokens` default 128 caps output per
call [F] → keep chunks ≤ ~20 s or raise it; Parakeet has full attention, so long chunks cost memory
quadratically [H]). Chunking per speaker turn makes inter-turn switching mostly monolingual per
chunk, which is exactly what auto-detection handles best.

**Recommendation: Option M.** It is the only one that can handle intra-sentence insertions at
all, it keeps one model in the catalogue, and it fits both `allineamento-parole-voci` strategies:
- Parakeet → token timestamps available → strategy A **or** B.
- Qwen3-ASR / Whisper (published exports) → no token timestamps → strategy A only
  (`Riconoscimento.token = null`).
Option R stays documented as a fallback only if a single multilingual model shows systematic
whole-chunk language errors (e.g. Parakeet emitting English/translit for Italian turns) that a
hint would fix — and then only with Qwen3-ASR's per-stream language hint, not by adding a second
ASR model.

## 6. Recommendation

**Parakeet TDT 0.6B v3 int8 via sherpa-onnx `nemo_transducer`, greedy search, CPU, fed per
diarization turn split by Silero VAD (≤ ~20–30 s), token timestamps kept** — provided the
measurement in §7 confirms (a) acceptable handling of English insertions in Italian speech, (b)
no systematic English bias on short Italian turns, (c) ASR phase ≤ 300 s/hour on the M3 Pro.
Measure **Qwen3-ASR 0.6B int8** alongside it as the code-switching challenger (replacing Whisper
turbo as the second main candidate: Whisper is expected to break the NFR on CPU and its published
exports give no token timestamps). Keep Whisper turbo only as a quality reference on the short
excerpts, if time allows. Drop Cohere, Canary (until a k2-fsa export + release after 1.13.8
exist), Omnilingual, SenseVoice, Moonshine, Zipformer.

Decision rule for the closure:
1. Parakeet passes the three conditions → choose Parakeet.
2. Parakeet fails (a) or (b) and Qwen3 passes them within ≤ 300 s → choose Qwen3-ASR; tell
   `allineamento-parole-voci` that token timestamps are unavailable (strategy A forced).
3. Only Whisper is acceptable and the pipeline misses 600 s → back to the user (as the spike
   node already says).

## 7. Measurement plan (closes the spike on the user's samples)

Environment: M3 Pro 36 GB, AC power, app/IDE idle. Everything runs **outside the repo** (scratch
dir), with the prebuilt sherpa-onnx **v1.13.8** tools (`sherpa-onnx-vad-with-offline-asr`,
`sherpa-onnx-offline`) or the `sherpa-onnx` pip wheel; nothing is committed; sample audio never
leaves the machine.

1. **Prepare inputs.** For each file in `sample/` (`New Recording 4.m4a`, `Via Roquel.m4a`):
   `ffmpeg -i <in> -ac 1 -ar 16000 -c:a pcm_s16le <scratch>/<name>.wav`; record duration and a
   rough language mix (minutes IT / EN, number of switches). If no file reaches 60 min, build the
   60-min benchmark input by concatenating samples (note it in the ADR).
2. **Reference excerpts (hand-checked).** The user picks 2 excerpts of ~5 min: E1 Italian-dominant
   with English insertions, E2 containing English turns/switches. The user writes the reference
   transcript (lowercase, no punctuation needed) and marks **≥ 20 switched phrases** (the English
   word/phrase plus its Italian context).
3. **Runs (same VAD for all).** Silero VAD (`silero_vad.onnx`), `max-speech-duration` 20 s,
   `--num-threads=6` (M3 Pro 36 GB = 6 P + 6 E cores; also try 4 and 8 once for Parakeet):
   - Parakeet v3 int8 (`--model-type=nemo_transducer`, `--decoding-method=greedy_search`);
   - Qwen3-ASR 0.6B int8 (auto language; second run with language hint `it` on E1 only, to learn
     whether a hint hurts English insertions);
   - Whisper turbo int8 on E1/E2 only (`language=""` auto; one run with `it`), CPU; CoreML EP once
     if the Parakeet/Qwen3 paths both fail quality.
4. **Quality metrics per excerpt.** WER after normalisation (lowercase, strip punctuation, numbers
   as words or digits consistently); **switched-phrase score**: each marked phrase classified as
   *correct* / *phonetically mangled* / *translated into Italian* / *dropped*; **wrong-language
   chunks** (a whole chunk emitted in the wrong language or translated); hallucinations on
   silence/noise (text on non-speech); for Parakeet, count of short (< 2 s) Italian chunks decoded
   as English.
5. **Speed/memory on the full 60 min.** Wall-clock of the ASR phase alone (VAD + recognition,
   model load included, reported separately), RTF = wall/audio, peak RSS (`/usr/bin/time -l`),
   model load time. **ORT check:** run Parakeet once with the libs of the
   `sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar` (via the Java API, or by comparing `lipo -info`
   on its `libonnxruntime*.dylib`); if it is universal2, measure the arm64-only ORT for comparison
   and hand the result to `packaging-modelli-desktop`.
6. **Timestamps sanity (Parakeet).** On E1: token timestamps monotonic, within the chunk, and a
   spot-check of 10 words against the audio (±200 ms) — input to `allineamento-parole-voci`.
7. **Acceptance thresholds (proposed; the user may adjust):** switched phrases *correct* ≥ 80 %,
   *translated* ≤ 5 %; WER on E1/E2 not worse than the best candidate by > 3 points absolute;
   zero wrong-language chunks > 5 s; ASR phase ≤ 300 s for 60 min; peak RSS ≤ 3 GB.

## 8. What the closure ADR should state

- **Status/links:** `closes_spike: scelta-asr-code-switching`; references ADR 0004, 0008 (c), 0011.
- **Chosen model + runtime config:** sherpa-onnx pinned version (≥ 1.13.8), model type
  (`nemo_transducer`), decoding (`greedy_search`), threads (measured best), EP = CPU.
- **Catalogue entry (ADR 0008 c):** `id` (e.g. `asr-parakeet-tdt-0.6b-v3-int8` — a new id on any
  SHA change), `ruolo` = riconoscimento, `url`
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2`,
  `formato` = `TAR_BZ2`, `sha256` + `dimensioneByte` **of the asset as downloaded at pin time**
  (compute them; not published by k2-fsa), files used inside the dir (`encoder.int8.onnx`,
  `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt`; `test_wavs/` ignored), `licenza` =
  CC-BY-4.0, `attribuzione` = "NVIDIA parakeet-tdt-0.6b-v3 (CC-BY-4.0), ONNX export by k2-fsa
  sherpa-onnx". (If Qwen3 wins: `sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2`, Apache-2.0,
  files `conv_frontend.onnx`, `encoder.int8.onnx`, `decoder.int8.onnx`, `tokenizer/`.)
- **Chunking contract:** ASR input = diarization turn split by Silero VAD at ≤ N s (measured N);
  strategy M (one multilingual model, no language routing); the VAD model is a separate catalogue
  entry owned by `vad-silero`.
- **Timestamps:** whether `Riconoscimento.token` is filled (Parakeet: yes, from
  `getTokens/getTimestamps/getDurations`, relative to the chunk) — the input to
  `allineamento-parole-voci`.
- **Measured numbers:** WER E1/E2, switched-phrase table, wrong-language count, RTF, ASR seconds
  per hour, peak RSS, and the ASR share of the 600 s budget (with the diarization configuration
  once known).
- **Accepted limitations:** residual code-switching errors are permanent in v1 (no text editing);
  Parakeet has no language hint.
- **Rejected alternatives and why** (table of §2) and the **fallback trigger** (e.g. switch to
  Qwen3-ASR if real use shows systematic wrong-language chunks).
- **Platform note for packaging:** INT8 speed on Apple Silicon requires an arm64-only ORT slice.

## 9. Open questions → residual spikes/checks

1. Does `sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar` bundle an arm64-only ORT or the slow
   universal2 slice? → `packaging-modelli-desktop` (can move ASR time ~3×).
2. Real IT+EN code-switching quality of Parakeet v3 and Qwen3-ASR 0.6B → §7 (no public data).
3. Parakeet English bias on short Italian turns (< 2 s) → §7 step 4; if real, merge short turns
   of the same `Voce` before ASR or fall back to Qwen3 with a hint.
4. Exact size/contents of `sherpa-onnx-whisper-turbo.tar.bz2` and `sherpa-onnx-qwen3-asr-0.6B-int8`
   (1.95 GB archive vs 937 MB extracted) → check at pin time (download size is a UX cost, ADR 0008).
5. Does sherpa expose Qwen3-ASR's detected language in `getLang()`? (Useful for the `.md`, not
   required.)
6. Whisper token timestamps via Java with an attention-enabled export: no published export found;
   irrelevant unless Whisper wins.

## Sources

- sherpa-onnx NeMo transducer models (Parakeet v3 URL, files, RTF, timestamps, `lang` field):
  https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html
- HF export listing: https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/tree/main
- NVIDIA Parakeet v3 model card (CC-BY-4.0, languages, auto LID, WER): https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3
- Canary-1B-v2 & Parakeet-TDT-0.6B-v3 paper: https://arxiv.org/html/2509.14128
- sherpa-onnx pretrained index / NeMo index: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html ,
  https://k2-fsa.github.io/sherpa/onnx/nemo/index.html
- sherpa Whisper export + turbo files: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/export-onnx.html ,
  https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/tree/main ,
  https://huggingface.co/csukuangfj/sherpa-onnx-whisper-large-v3/tree/main
- Whisper timestamps in sherpa: https://github.com/k2-fsa/sherpa-onnx/discussions/2942 ,
  https://github.com/k2-fsa/sherpa-onnx/pull/2945 , https://github.com/k2-fsa/sherpa-onnx/issues/2410
- sherpa-onnx CHANGELOG / releases (v1.13.8, JNI jars): https://github.com/k2-fsa/sherpa-onnx/blob/master/CHANGELOG.md ,
  https://github.com/k2-fsa/sherpa-onnx/releases , https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
- Java API: https://github.com/k2-fsa/sherpa-onnx/tree/master/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx
  (`OfflineRecognizerResult.java`, `OfflineWhisperModelConfig.java`, `OfflineQwen3AsrModelConfig.java`)
- Qwen3-ASR in sherpa: https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/index.html ,
  https://k2-fsa.github.io/sherpa/onnx/qwen3-asr/pretrained.html ; model card https://huggingface.co/Qwen/Qwen3-ASR-0.6B ;
  report https://arxiv.org/html/2601.21337
- Cohere Transcribe: https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/index.html ,
  https://k2-fsa.github.io/sherpa/onnx/cohere_transcribe/pretrained.html , https://huggingface.co/CohereLabs/cohere-transcribe-03-2026
- Canary language map fix: https://github.com/k2-fsa/sherpa-onnx/pull/3962
- Omnilingual ASR: https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html
- SenseVoice / Moonshine languages: https://k2-fsa.github.io/sherpa/onnx/sense-voice/index.html ,
  https://k2-fsa.github.io/sherpa/onnx/moonshine/index.html
- Spoken language identification: https://k2-fsa.github.io/sherpa/onnx/spoken-language-identification/pretrained_models.html
- Apple Silicon universal2 ORT slowdown: https://github.com/OpenWhispr/openwhispr/pull/2116
- Parakeet English-bias report: https://github.com/FluidInference/FluidAudio/issues/303
- Code-switching opinion (not measurement): https://spokenly.app/blog/parakeet-vs-whisper
- Whisper turbo CPU timing (third-party): search result summarised via
  https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/index.html thread (i5-13th, 22 s in 5.98 s) — weak source, [H]-grade.
