# Research — scelta-diarizzatore (sherpa-onnx speaker diarization for the `Voce`s)

- **Feature:** trascrizione-con-parlanti · **Release:** R1 (critical path)
- **Spike:** `tasks/app/backlog/scelta-diarizzatore.md`
- **Consumers (FEEDS):** the closing ADR of spike `scelta-diarizzatore` (gates block
  `diarizzatore-sherpa`); spikes `allineamento-parole-voci` (overlap/miss behaviour) and
  `impronta-vocale-affidabilita` (embedding reuse); `benchmark-elaborazione` (ADR 0011 budget share);
  spike `attesa-mutex-estrazione` (length of the diarization native call).
- **Date:** 2026-09-23. Researched from public sources only; **nothing was run on the M3 Pro yet**
  (no shell in this session). Every performance / quality number below is either a cited third-party
  measurement or explicitly marked **[hypothesis]**. The spike is therefore **advanced, not closed**:
  closure needs the measurement plan (section 8).

---

## 1. The question (from the spike node)
Which configuration of sherpa-onnx `OfflineSpeakerDiarization` (segmentation model + embedding
extractor + clustering) produces the `Voce`s of a `Trascritto` for IT/EN work meetings with 2–4
speakers, overlapping speech, within the ADR 0011 budget (60-min recording ≤ 600 s end-to-end on the
M3 Pro CPU)? Licence of each weight; catalogue entry for ADR 0008 (URL + SHA-256 + licence + format).

## 2. How sherpa-onnx diarization actually works (facts, from source)
Source: `sherpa-onnx/csrc/offline-speaker-diarization-pyannote-impl.h`, `fast-clustering.cc`,
`fast-clustering-config.cc`, `offline-speaker-diarization.h`,
`offline-speaker-segmentation-pyannote-model-config.{h,cc}`, `jni/offline-speaker-diarization.cc`,
`kotlin-api/OfflineSpeakerDiarization.kt` (master, Sept 2026).

1. **Segmentation** (pyannote powerset model, 10 s window at 16 kHz): the audio is cut into windows
   stepping by `window_shift_ratio × window` (default **0.1 → 1 s step**; configurable since
   **v1.13.5**, exposed in the C API and language bindings — incl. Kotlin
   `OfflineSpeakerSegmentationPyannoteModelConfig.windowShiftRatio` — since **v1.13.6**; valid range
   `(0, 1]`, "smaller = more overlap, higher quality, slower"). Each window yields up to **3 local
   speakers, ≤ 2 simultaneous per frame** (segmentation-3.0 model card).
2. **Embeddings:** for every (window, local speaker) pair with ≥ 10 active frames, one embedding is
   computed on that speaker's samples **with overlapped frames excluded** (`ExcludeOverlap`: keeps
   frames where < 2 speakers are active). Computed **sequentially, one stream at a time** (no
   batching); speed comes only from onnxruntime intra-op threads (`numThreads`).
3. **Clustering (`FastClustering`):** L2-normalised embeddings, **cosine distance** (`1 − cos`),
   **complete-linkage** agglomerative clustering (fastcluster `hclust_fast`). If
   `numClusters > 0` → `cutree_k` (exact count); else `cutree_cdist` at **`threshold` = a distance
   cut-off: smaller → more speakers, larger → fewer**. Kotlin default `threshold = 0.5f`; the CLI's
   own "unknown number of speakers" example uses `0.90`. There is **no minimum-cluster-size /
   small-cluster reassignment** step (pyannote 3.1 has `min_cluster_size`; community-1 uses VBx).
4. **Reconstruction:** per frame, the estimated number of active speakers (rounded average over
   overlapping windows) selects the top-k global speakers → **k can be 2: overlapping speech IS
   emitted as two time-overlapping segments of different speakers.** Segments shorter than
   `min_duration_on` are dropped; same-speaker gaps shorter than `min_duration_off` are merged.
   Defaults: C++ `min_duration_on = 0.3 s`, `min_duration_off = 0.5 s`; **Kotlin data class
   default `minDurationOn = 0.2f`** (differs from C++ — set it explicitly).
5. **Output via JNI/Kotlin:** `process(samples: FloatArray)` / `processWithCallback(samples,
   (numProcessedChunks, numTotalChunks, arg) -> Int)` → `Array<OfflineSpeakerDiarizationSegment>`
   with `start`, `end` (float **seconds**), `speaker` (Int) and — since **v1.13.8** — a per-segment
   `confidence`; the JNI calls `SortByStartTime()` before returning. **The internal embeddings are
   NOT returned** (only segments).
6. The whole recording is **one blocking native call** (minutes for 60 min of audio).

Implications for the pinned port `Diarizzatore.diarizza(CampioniAudio): List<Turno>`:
- `Turno(IntervalloMs, voceIndice)` maps 1:1 from a segment (seconds → ms, rounded; drop
  zero-length after rounding). Overlapping turns are legal ([INV-7], Q-4) — no trimming needed.
- `voceIndice` may have gaps if a speaker's segments were all dropped by `min_duration_on`; harmless
  (the `trascritto` aggregate maps by first appearance).
- The port has **no speaker-count parameter**: `numClusters` stays `-1` in v1 (see §6).
- For `attesa-mutex-estrazione` option (b): the diarization phase **cannot release the Mutex
  mid-call** (global clustering needs the whole file); chunking the file into separate `process`
  calls would break cross-chunk speaker identity. Only (a) or (c) are compatible with diarization.

## 3. Candidate models — assets, sizes, licences

### 3.1 Segmentation (release `speaker-segmentation-models`, no `checksum.txt` published)
| Asset | Format | Size | Contents | Licence | Verdict |
|---|---|---|---|---|---|
| `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2` | `TAR_BZ2` | 6.64 MB | `model.onnx` (~6.0 MB), `model.int8.onnx` (~1.5 MB), `LICENSE`, `README.md` | **MIT** (pyannote/segmentation-3.0; HF gate = contact-info collection, not a licence term; the k2-fsa re-host is ungated) | **Candidate (only one)** |
| `.../sherpa-onnx-reverb-diarization-v1.tar.bz2` | `TAR_BZ2` | 10.4 MB | `model.onnx`, `model.int8.onnx`, LICENSE | Rev **non-commercial** licence ("accessible under a non-commercial license" — sherpa docs + converter README; HF "License: other") | **Dropped** (spike rule: drop if non-commercial) |
| `.../sherpa-onnx-reverb-diarization-v2.tar.bz2` | `TAR_BZ2` | 242 MB | — | same Rev non-commercial family | **Dropped** |

SHA-256 of the segmentation archive: **not published** by k2-fsa → compute locally at pin time
(`shasum -a 256`) and record in the ADR.

### 3.2 Embedding extractors (release `speaker-recongition-models` — sic, the typo is in the tag)
Single un-archived `.onnx` files (`FILE` format per ADR 0008 (c)). SHA-256 values below are copied
**verbatim from the release's `checksum.txt`** (64 hex = SHA-256); they must still be re-verified on
the downloaded bytes at pin time. URL prefix:
`https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/`

| File | Size | Params / VoxCeleb1-O EER (upstream) | Weight licence | SHA-256 (checksum.txt) | Role here |
|---|---|---|---|---|---|
| `wespeaker_en_voxceleb_resnet34_LM.onnx` | 25.3 MB | ResNet34 ~6.6 M, large-margin fine-tuned | **CC-BY-4.0** (WeSpeaker: "pretrained model on VoxCeleb follows CC-BY-4.0") | `e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012` | **Primary candidate** — it is the embedding of pyannote 3.1 (`pyannote/wespeaker-voxceleb-resnet34-LM`, same pairing with segmentation-3.0); also the choice of FluidAudio / Soniqo ports |
| `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` | 28.2 MB | CAM++ 7.2 M / 0.65 % | Apache-2.0 (3D-Speaker repo); trained on VoxCeleb (CC-BY-4.0 data) — **verify model card at pin time** | `357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b` | **Challenger** (light, low EER) |
| `nemo_en_titanet_small.onnx` | 38.4 MB | TitaNet-S | CC-BY-4.0 (NVIDIA NGC/HF) | `ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e` | **Speed candidate** (fastest in sherpa's own RTF table, §4) |
| `wespeaker_en_voxceleb_CAM++_LM.onnx` | 27.9 MB | CAM++ LM | CC-BY-4.0 | `e197af7e9d473030cf486b3124149a19bf37014d0e4485e4c70c483b0ec10cb2` | optional 4th |
| `wespeaker_en_voxceleb_resnet34.onnx` | 25.3 MB | ResNet34 (no LM) | CC-BY-4.0 | `5ef208a9da1453335308a6b6f4e6dfbd7e183a38b604de0a57664f45d257fe94` | superseded by `_LM` |
| `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx` | 25.3 MB | ERes2Net (VoxCeleb) | Apache-2.0 (verify) | `c59158379255ad66e161679cca6af8d52d51e389e3224ab7d7a7baae295c2db5` | optional |
| `nemo_en_titanet_large.onnx` | 96.7 MB | 23 M / 0.66 % | CC-BY-4.0 | `d51abcf31717ef28162f26acb9d44dd4127c3d44c9b8624f699f3425daca8e77` | too heavy for diarization (thousands of calls); **print-only** candidate for `impronta-vocale-affidabilita` |
| `wespeaker_en_voxceleb_resnet{152,221,293}_LM.onnx` | 75–109 MB | larger ResNets | CC-BY-4.0 | in checksum.txt | print-only candidates |
| `3dspeaker_*_zh-cn_*`, `wespeaker_zh_cnceleb_*`, `eres2netv2_sv_zh-cn` | 25–210 MB | Chinese-trained | Apache-2.0 / CC-BY | in checksum.txt | **Not preferred** for IT/EN (the sherpa docs' examples use the zh-cn ERes2Net — do not copy blindly) |

All candidates accept 16 kHz mono (= `CampioniAudio`). None of the embedding files has an int8
variant in the release (only the segmentation archive has `model.int8.onnx`).

Attribution text for the "Licenze dei modelli e librerie" screen (ADR 0008): pyannote
segmentation-3.0 — MIT, © pyannote (Hervé Bredin); WeSpeaker ResNet34-LM — CC-BY-4.0, WeSpeaker
team, trained on VoxCeleb (CC-BY-4.0, Nagrani/Chung/Zisserman); CAM++ — Apache-2.0, 3D-Speaker
(Alibaba DAMO); TitaNet — CC-BY-4.0, NVIDIA.

## 4. Speed vs ADR 0011 (facts + [hypothesis])
Published numbers (sherpa docs, `speaker-diarization/models.html`; 56.9 s 4-speaker zh clip,
`num-clusters=4`, default threads — machine not stated):

| Segmentation | Embedding | RTF |
|---|---|---|
| seg-3.0 fp32 | 3D-Speaker ERes2Net-base (zh) | 0.297 |
| seg-3.0 int8 | 3D-Speaker ERes2Net-base (zh) | 0.241 |
| seg-3.0 fp32 | NeMo TitaNet-small | 0.119 |
| seg-3.0 int8 | NeMo TitaNet-small | 0.110 |
| reverb-v1 fp32/int8 | 3D-Speaker / NeMo | 0.170 – 0.452 |

Issue #2355: seg-3.0 + ERes2Net-base on an i7-1165G7 laptop → **RTF 0.2 on CPU** (DirectML 10+:
GPU EP made it 50× slower).

**Budget arithmetic.** ADR 0011: 600 s for decode + diarization + ASR + alignment on 60 min. At
the published default-config RTFs, diarization alone would take **7–18 min/h** — i.e. **it would
blow the whole budget on its own**. Knobs that cut it, all [hypothesis until measured]:
- **Threads:** the default is `numThreads = 1`; ADR 0004 sets intra-op threads = performance cores
  (M3 Pro: 5 or 6 P-cores). Expected 2–4× on the embedding stage (small models scale sub-linearly).
- **`windowShiftRatio`:** embedding calls ≈ windows × active local speakers. 60 min at 0.1 →
  ~3 600 windows → **~4–7 k embedding calls**; at 0.5 → ~720 windows → ~1–1.5 k calls (≈ 5× fewer).
  arXiv 2606.08505 (pyannote-3.1-style pipeline, CAM++) reports coarser stride + per-chunk
  embeddings **DER-neutral on AMI (meetings)** but harmful in-the-wild because of speaker
  under-counting; our data is meetings → promising, must be measured.
- **Lighter embedding:** TitaNet-small was ~2.5× faster than ERes2Net-base in sherpa's table.
- **int8 segmentation:** ~10–20 % saving; segmentation is not the bottleneck.

**[hypothesis] realistic target:** diarization ≤ **~150–180 s/h** (RTF ≤ 0.05) on the M3 Pro with
6 threads and `windowShiftRatio` 0.25–0.5, leaving ≥ 400 s for ASR (Parakeet v3 int8 is expected
well inside that; Whisper-turbo is not). This split is the key number the closing ADR must fix
together with `scelta-asr-code-switching`.

**Memory [hypothesis]:** weights are tiny (seg ≤ 6 MB + emb 25–40 MB). Peak is the audio itself:
60 min × 16 kHz × 4 B = **~230 MB FloatArray on the heap + a native copy** across JNI → ~0.5 GB for
the diarization phase, plus powerset matrices (small). Within ADR 0004's "~1–2 GB off-heap" note,
but `-Xmx` must allow the 230 MB array. Measure peak RSS.

**CoreML:** only a comparison (ADR 0004/0011). No published sherpa-diarization CoreML numbers found;
the DirectML report shows GPU EPs can be dramatically slower for these small, many-call models.
[hypothesis] CoreML gives little or negative gain here.

## 5. Expected quality on meeting audio, and failure modes
**Reference DERs (not directly transferable):**
- pyannote 3.1 (same segmentation + same WeSpeaker ResNet34-LM, but **centroid linkage +
  `min_cluster_size` + its own tuned threshold**): AMI headset 18.8 %, AMI array 22.4 %, DIHARD3
  21.7 %, AliMeeting 24.4 %, VoxConverse 11.3 %, MSDWild 25.3 % (HF model card).
- 3D-Speaker's own recipe: AMI-SDM 21.8 %, AliMeeting 19.7 %, VoxConverse 11.8 %.
- community-1 (VBx + PLDA) is better than 3.1 "especially on speaker counting and confusion", but is
  Python/CoreML only — excluded by ADR 0004.
- TitaNet-L's advertised AMI DER ~1.7–2 % is with oracle VAD/segments — **not comparable**.

**[hypothesis] expected DER with sherpa on the user's recordings: ~15–30 %**, the confusion part
likely worse than pyannote 3.1 because sherpa's complete-linkage threshold clustering lacks the
small-cluster reassignment. GitHub issue #1708 (open) reports sherpa + WeSpeaker ResNet34-LM
**collapsing a 2-speaker file into 1 speaker** where pyannote 3.0 separated them — i.e. the default
threshold is not portable across embedding models; it must be tuned per model.

**Failure modes to expect and to log in the measurement:**
| Mode | Mechanism | Remedy in snastro |
|---|---|---|
| **Speaker over-count** (extra `Voce` with few seconds) | complete linkage on thousands of embeddings of a long recording; no min-cluster-size; far-field/noisy windows form their own cluster | `Revisione` → `unire` (one action). Optionally a Kotlin post-filter (§6) |
| **Speaker under-count** (two people in one `Voce`) | threshold too high; similar voices (same gender/age/accent); coarse window shift (arXiv 2606.08505) | `Revisione` → `dividi`/`riassegna` (more work than `unire`) → **bias the threshold toward slight over-count** |
| **Short turns lost** (backchannels "sì", "ok", "yeah") | segments < `min_duration_on` dropped; < 10 active frames get no embedding | accepted; with alignment strategy (A) the missed speech is **not transcribed** at all → input for `allineamento-parole-voci` |
| **Crosstalk** | segmentation handles ≤ 2 simultaneous speakers; overlapped frames excluded from embeddings (good) but 3-way crosstalk collapses | overlapping `Turno`s emitted → [INV-7] allows them |
| **Language switch** | embeddings are largely language-independent; VoxCeleb is multilingual-celebrity English-dominant | prefer VoxCeleb/English-trained over zh-cn models |
| **Far-field phone recording** (`.m4a` voice memos) | lower SNR, reverberation → embeddings drift → over-count | measured on the user's own files |

## 6. Clustering: known vs unknown speaker count
- **Unknown (v1 default): `numClusters = -1`, tuned `threshold`.** The threshold is model-specific
  (cosine *distance*, complete linkage) — sweep it per embedding model; the Kotlin default 0.5 and
  the CLI example 0.90 are just two points.
- **Known count (`numClusters = k`)**: most robust against over/under-count, but the pinned port
  has no parameter and the UI has no field. **Report as an option only**: if measurement shows the
  threshold is unstable across recordings, the fallback is a `numeroParlantiAtteso: Int?` hint
  (would change the port, the `Registrazione` input and S-screens → a separate decision).
- **Optional Kotlin post-filter [hypothesis, only if §8 shows spurious tiny clusters]:** in the
  adapter, relabel a speaker whose total duration < N s to the nearest speaker by centroid cosine,
  using extra `SpeakerEmbeddingExtractor` calls on bounded audio (≤ 30 s, same rule as
  `SorgenteImpronta`). This approximates pyannote's `min_cluster_size` without leaving sherpa.
  Pure-Kotlin centroid/VBx re-clustering is **not possible** on sherpa's internal embeddings (not
  exposed).
- **Confidence (v1.13.8):** per-segment confidence (silhouette-based, `compute_confidence`) exists;
  showing probabilities is out of v1 scope (product brief), but it could internally flag doubtful
  segments later. Not required.

## 7. Can the SAME embedding model serve the voice prints (Parlanti, R2)?
**Facts:**
- The diarizer's per-window embeddings are **not returned** by the Kotlin/JNI API. So "reuse" can
  only mean **the same weights**, never the same vectors: every `ImprontaVocale` is a **second,
  separate extraction** through `SpeakerEmbeddingExtractor` on `SorgenteImpronta` audio (≤ 30 s
  per `Voce`, ADR 0012 (b)). Cost: 2–4 `Voce`s × ≤ 30 s → **~1 s total per recording** [hypothesis]
  — negligible in ADR 0011.
- sherpa ships both APIs with the same model file format; one `.onnx` can back both
  `OfflineSpeakerDiarization`'s `embedding` config and `SpeakerEmbeddingExtractor`.

**Implications:**
1. **One download, one catalogue entry** if shared — but ADR 0008's `VoceCatalogo` has a single
   `ruolo`. The closing ADR must say either "one entry, two roles" or keep two adapter config keys
   pointing at the same catalogue `id` (preferred: no duplicate download, no schema change).
2. **Coupling through `EstrattoreImpronta.modello`:** if shared, *any* swap of the diarizer's
   embedding (e.g. to fix DER) changes the print model id → **every stored print goes stale** →
   `RiallineaTutteLeImpronte` re-derives them (ADR 0012 (b): safe, no migration, but re-decodes
   audio of every attributed `Voce` of the open project). Acceptable, but it means a diarization
   tweak has a Parlanti-wide effect. **Recommendation: keep the two roles independently
   configurable** in `:avvio`, pointing at the same id *today*.
3. **Different optimisation targets:** diarization needs a *fast* model (thousands of calls,
   within-recording, same channel); prints need a *channel-robust* model (few calls, cross-device /
   cross-room / cross-session). A heavier model (TitaNet-L, ResNet293-LM, ERes2Net-large) costs
   almost nothing for prints but would blow diarization's budget. So the diarizer's pick is the
   **first** candidate for prints (spike `impronta-vocale-affidabilita` says: test reuse first), not
   a constraint.
4. **Thresholds do not transfer:** the diarization `threshold` (cosine distance, complete linkage,
   short windows, one recording) and `SoglieFascia` (cosine similarity of ≤ 30 s prints across
   sessions) are calibrated separately.
5. Licence is the same obligation either way (CC-BY-4.0 attribution for WeSpeaker/TitaNet).
   Voice prints are biometric data of third parties (ADR 0009) — unaffected by which model.

## 8. Measurement plan on the user's samples (to close the spike)
**Prerequisites / blockers**
- `sample/` currently holds **2** recordings (`New Recording 4.m4a`, `Via Roquel.m4a`); the
  closure criterion requires **≥ 3**, and ADR 0011 requires one **~60-min** recording. **The user
  must add at least one more** (ideally with ≥ 2 people recurring across files — the same set
  then serves `impronta-vocale-affidabilita`). Durations/speaker counts of the two files are unknown
  to this research.
- Pin a sherpa-onnx release **≥ v1.13.6** (window shift ratio in the Kotlin API; v1.13.8 if the
  confidence field is wanted; latest seen: v1.13.8, 2026-09-10). Same version as the
  `packaging-modelli-desktop` spike.
- Convert once, outside the repo (scratch dir; never commit audio):
  `afconvert -f WAVE -d LEI16@16000 -c 1 in.m4a out.wav` (macOS-native; no ffmpeg needed).

**Step 1 — Reference labels.** For each recording, hand-label a **10-min excerpt** (≥ 30 min
total) as RTTM: every turn incl. backchannels and overlaps (Audacity label track → RTTM). Also note
the **true speaker count** of the full recording. Choose excerpts containing crosstalk and an IT↔EN
switch.

**Step 2 — Sweep (fast iteration with the prebuilt CLI, same C++ core as JNI).**
`sherpa-onnx-offline-speaker-diarization` (flags: `--segmentation.pyannote-model`,
`--embedding.model`, `--clustering.num-clusters` / `--clustering.cluster-threshold`; thread,
min-duration and window-shift-ratio flag names to confirm with `--help`):
- segmentation {`model.onnx`, `model.int8.onnx`}
- embedding {WeSpeaker ResNet34-LM, 3D-Speaker CAM++ VoxCeleb, NeMo TitaNet-small, (WeSpeaker CAM++-LM)}
- threshold 0.40 → 1.00 step 0.05 (and, as a reference only, `num-clusters = true count`)
- `windowShiftRatio` {0.1, 0.25, 0.5}
- `minDurationOn` 0.3, `minDurationOff` 0.5 (then ±0.2 on the winner)
- threads {1, 4, P-cores}

**Step 3 — Metrics per run.**
- **DER** vs the RTTM (collar 0.25 s, overlap scored; also report no-collar), split **miss / FA /
  confusion**; use `pyannote.metrics` in a throwaway venv in the scratch dir (tooling only, never an
  app dependency) or a ~50-line frame-based DER. Fallback metric from the node: wrong turns per
  10 min (visual count).
- **Speaker-count accuracy:** estimated − true per full recording; number of "tiny" speakers
  (< 30 s total).
- **Wall-clock and RTF** on the **full 60-min** file, model load included; per-stage timing via
  `debug=true` (prints per-stage timing since v1.13.5).
- **Peak RSS** (`/usr/bin/time -l`), and for the JVM run heap + RSS.
- CPU vs `provider=coreml` on the winner only.

**Step 4 — Choose.** Pick the threshold with the lowest mean DER **that is also flat** (±0.05 does
not change the speaker count), using leave-one-recording-out (tune on 2, check on the 3rd) —
with only 3 files, overfitting the threshold is the main risk. Prefer slight over-count to
under-count (`unire` is cheaper than `dividi`).

**Step 5 — Confirm through the real path.** Re-run the winner once through the Kotlin/JNI API
(`OfflineSpeakerDiarization` with explicit `minDurationOn`, `windowShiftRatio`, `numThreads`) in a
scratch `@Tag("modelli")` run: identical segments to the CLI, wall-clock, RSS, all handles closed.

**Proposed acceptance bars** (to confirm with the user in the ADR — not facts):
DER ≤ 25 % and confusion ≤ 10 % on the excerpts; speaker count exact on ≥ 2 of 3 recordings and
never under-counted; diarization ≤ ~180 s per hour on the M3 Pro CPU. If not met → the spike's
fallback ladder (tune → rely on `Revisione` → later superseding ADR for an out-of-process
pyannote-community-1 adapter).

## 9. RECOMMENDATION (to be confirmed by §8)
- **Segmentation:** `sherpa-onnx-pyannote-segmentation-3-0` (MIT, `TAR_BZ2`), file
  **`model.int8.onnx`** unless its DER is measurably worse than `model.onnx`. Reverb v1/v2
  **dropped** (non-commercial).
- **Embedding:** **`wespeaker_en_voxceleb_resnet34_LM.onnx`** (CC-BY-4.0, 25.3 MB, `FILE`) as
  primary — the exact pairing pyannote 3.1 was tuned with, VoxCeleb/English-trained; challengers
  **3D-Speaker CAM++ VoxCeleb** (Apache-2.0) and **NeMo TitaNet-small** (CC-BY-4.0, fastest). Take
  the one with lowest DER within the time bar; ties → CAM++ (lightest).
- **Clustering:** `numClusters = -1`, `threshold` = the §8 winner (expect somewhere in 0.5–0.9;
  unknown until measured), biased to slight over-count. No speaker-count hint in v1 (reported as an
  option). `minDurationOn = 0.3` (set explicitly — Kotlin default is 0.2), `minDurationOff = 0.5`.
- **Speed knobs:** `numThreads` = P-cores for both segmentation and embedding; `windowShiftRatio`
  = the largest of {0.1, 0.25, 0.5} whose DER stays within +1 pt of 0.1. CPU provider.
- **Embedding reuse:** use the same file for `EstrattoreImpronta` as the **first** candidate, but
  keep the two roles independently configurable (same catalogue id today).

## 10. What the closure ADR should state
1. `closes_spike: scelta-diarizzatore`; the chosen segmentation file, embedding file, and **every**
   config value (`threshold`, `numClusters=-1`, `minDurationOn`, `minDurationOff`,
   `windowShiftRatio`, `numThreads`, `provider=cpu`), plus the pinned **minimum sherpa-onnx
   version** (≥ 1.13.6).
2. Catalogue entries (ADR 0008 (c)): per model — `id` (a **new id for any SHA change**), `ruolo`,
   URL, `formato` (`TAR_BZ2` for segmentation, `FILE` for embedding), **SHA-256 of the asset as
   downloaded and verified locally** (segmentation: computed, none published; embedding: matches
   `checksum.txt`), size in bytes, licence + attribution text, and the **file name inside
   `percorso(id)`** the adapter loads (`model.int8.onnx` / the `.onnx` name).
3. The measured evidence table: per recording DER (miss/FA/confusion), speaker-count error, RTF /
   wall-clock on the 60-min file, peak RSS; CPU vs CoreML if tried; the rejected configurations and
   why.
4. The **diarization share of the 600 s budget**, stated jointly with the ASR spike's number.
5. Behaviour contract for the adapter: seconds → ms rounding, overlapping `Turno`s emitted (↔
   [INV-7]), sorted by start, `voceIndice` gaps allowed, empty result on silence (0 `Turno`s — what
   the pipeline does then), one native call held under the Mutex for the whole phase (→ input to
   `attesa-mutex-estrazione`: option (b) impossible for this phase).
6. The embedding-reuse position: whether `EstrattoreImpronta` starts with the same id (final word
   to `impronta-vocale-affidabilita`), the two roles independently configurable, the staleness
   consequence (ADR 0012 (b)) of any future swap.
7. Accepted failure modes (short backchannels dropped, ≤ 2-way overlap, over-count fixed by
   `unire`) and the **fallback triggers** (which measured values would reopen the decision:
   post-filter → count hint → superseding ADR for pyannote-community-1 out-of-process).

## 11. Open questions → spikes / measurement
- Actual DER / count accuracy on the user's recordings, and the threshold value (§8) — **needs a
  3rd recording + a 60-min one**.
- Actual RTF on the M3 Pro with threads + window shift (ADR 0011 split with ASR).
- Is `windowShiftRatio` 0.5 DER-neutral on these meetings (arXiv 2606.08505 says yes on AMI)?
- Does the tiny-cluster post-filter (§6) earn its keep, or is `Revisione` enough?
- Whether ResNet34-LM is channel-robust enough for prints — owned by `impronta-vocale-affidabilita`.
- Model-card licence of the 3D-Speaker VoxCeleb exports (Apache-2.0 assumed from the repo) — check
  only if CAM++ wins.

## Sources
- sherpa-onnx speaker diarization docs: https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/index.html ;
  models + RTF table: https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/models.html
- Segmentation assets: https://github.com/k2-fsa/sherpa-onnx/releases/expanded_assets/speaker-segmentation-models
- Embedding assets: https://github.com/k2-fsa/sherpa-onnx/releases/expanded_assets/speaker-recongition-models ;
  checksums: https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/checksum.txt
- Algorithm source: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/offline-speaker-diarization-pyannote-impl.h ,
  `fast-clustering.cc`, `fast-clustering-config.cc`, `offline-speaker-diarization.h`,
  `offline-speaker-segmentation-pyannote-model-config.{h,cc}`, `jni/offline-speaker-diarization.cc`,
  `kotlin-api/OfflineSpeakerDiarization.kt`, CLI `sherpa-onnx-offline-speaker-diarization.cc` (same repo, master)
- Release notes v1.13.5 / v1.13.6 / v1.13.8: https://github.com/k2-fsa/sherpa-onnx/releases
- Issue #2355 (CPU RTF 0.2 / DirectML): https://github.com/k2-fsa/sherpa-onnx/issues/2355
- Issue #1708 (sherpa vs pyannote 3.0 mismatch): https://github.com/k2-fsa/sherpa-onnx/issues/1708
- pyannote/segmentation-3.0 (MIT, powerset, 10 s, ≤3 spk / ≤2 overlap): https://huggingface.co/pyannote/segmentation-3.0
- pyannote/speaker-diarization-3.1 (DER table, MIT): https://huggingface.co/pyannote/speaker-diarization-3.1
- pyannote/wespeaker-voxceleb-resnet34-LM (CC-BY-4.0): https://huggingface.co/pyannote/wespeaker-voxceleb-resnet34-LM
- WeSpeaker pretrained licence: https://github.com/wenet-e2e/wespeaker/blob/master/docs/pretrained.md
- 3D-Speaker (Apache-2.0, EER table, diarization DER): https://github.com/modelscope/3D-Speaker
- NVIDIA TitaNet-L (CC-BY-4.0): https://huggingface.co/nvidia/speakerverification_en_titanet_large
- Reverb (non-commercial): https://huggingface.co/csukuangfj/sherpa-onnx-reverb-diarization-v1/blob/main/README.md ,
  https://huggingface.co/Revai/reverb-diarization-v1
- pyannote community-1 (VBx, CC-BY-4.0): https://huggingface.co/pyannote/speaker-diarization-community-1 ;
  CoreML/Swift ports: https://soniqo.audio/guides/diarize
- Stride acceleration vs under-counting: https://arxiv.org/abs/2606.08505
- sherpa diarization in a local app (short-utterance quality, ResNet34 choice): https://github.com/zajca/zwhisper/issues/14
- Internal: ADR 0004, 0008 (+ amendment c), 0011, 0012 (b); spike nodes `scelta-diarizzatore`,
  `impronta-vocale-affidabilita`, `allineamento-parole-voci`, `attesa-mutex-estrazione`,
  `scelta-asr-code-switching`; block `diarizzatore-sherpa` (pinned `Diarizzatore`/`Turno` types);
  product brief; profile.
