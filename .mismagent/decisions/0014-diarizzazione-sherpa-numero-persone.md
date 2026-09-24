---
scope: global
status: accepted
supersedes: null   # partial: the auto-start policy of ADR 0012 Amendment R2 / ADR 0004 Execution — superseded in place by their dated amendments, pointing here
closes_spike: scelta-diarizzatore
enforced_by: null
amended: 2026-09-24   # see also "Amendment 2026-09-24 (c)" → ADR 0019 (segmentation file and clustering rows, Embedding reuse superseded; TitaNet-S entry added). 2026-09-23 user decision: no automatic start on import; Trascrivi/Riprova carry Numero di persone 1..10. 2026-09-24: see "Amendment 2026-09-24 — numClusters above the real count" and "Amendment 2026-09-24 (b)" (Ritrascrivi, ADR 0018)
---
# 0014 — Diarization: sherpa-onnx pyannote-3.0 + WeSpeaker ResNet34-LM, threshold 0.4; optional "Numero di persone" → `num_clusters`

## Context
Spike `scelta-diarizzatore` asked which sherpa-onnx `OfflineSpeakerDiarization` configuration
produces the `Voce`s of a `Trascritto` for IT/EN meetings with 2–4 speakers, within ADR 0011.
ADR 0004 fixes the runtime, and ADR 0008 (c) fixes the catalogue.

Evidence:
- `features/trascrizione-con-parlanti/research/scelta-diarizzatore.md`. This is the research on
  candidates and licences. Reverb v1/v2 were dropped because they are non-commercial.
- `features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md`. It holds the §3
  threshold × window-shift sweep on two 5-min excerpts, the §5 full-file run, the "User judgement",
  and the "Follow-up: speaker count and music".

Measured on 2026-09-23 (M3 Pro, sherpa-onnx 1.13.8, CPU, 6 threads). All runs were **under heavy
load**, so read every time as an upper bound.
- **`threshold` is a distance cut-off in `FastClustering`.** A higher threshold gives fewer
  speakers. At 0.5–0.9 almost everything collapses to 1 speaker.
- **Window-shift ratio.** `wsr = 0.5` is about 4–5× faster than 0.1, with no visible loss of
  separation on the excerpts. Diarization RTF was 0.02–0.03 on the excerpts and **0.054 on the full
  file** (244.7 s for 4511 s, load 15–79).
- **Excerpts, `wsr 0.5 / th 0.4`.** 2 speakers on E1, stable across th 0.3–0.45. 4 clusters on E2
  (3 main voices plus 2 s of interjections).
- **Full Via Roquel (75 min), which has 4 real speakers [user].** Automatic clustering gave
  **10 clusters**: 974, 851, 433, 379, 315, 311, 37, 15, 6 and 2 s. That is an over-count.

| Variant, full Via Roquel | clusters | speech s per cluster |
|---|---|---|
| auto (th 0.4, wsr 0.5) | 10 | 974 … 2 |
| **A: `num_clusters = 4`** | **4** | 1874, 737, 367, 349 (diarization wall 67 s) |
| B: music removed (CED-mini tagging) + auto | 12 | 1327 … 2 |
| B + merge clusters < 3 % into nearest | 5 | 1378, 771, 446, 337, 221 |
| B + `num_clusters = 4` | 4 | 2118, 664, 323, 53 |
| C: stricter Silero VAD (0.6) + auto | 14 | 973 … 3 |

A known speaker count was the only variant that gave the true number. Auto clustering over-splits
long recordings, with or without music.

## Decision
- **Runtime config** (sherpa-onnx `OfflineSpeakerDiarization`, **≥ 1.13.8**; the Kotlin
  `windowShiftRatio` needs ≥ 1.13.6):

  | Setting | Value |
  |---|---|
  | Segmentation | pyannote **segmentation-3.0**, file **`model.int8.onnx`**, `windowShiftRatio = 0.5` |
  | Embedding | **WeSpeaker ResNet34-LM (VoxCeleb)** |
  | Clustering | `FastClustering`, **`threshold = 0.4`** (distance cut-off) |
  | `numClusters` | see "Numero di persone" below |
  | Durations | `minDurationOn = 0.3`, `minDurationOff = 0.5`. Both are set explicitly, because the Kotlin defaults differ. |
  | Threads | `numThreads` = performance cores (6 on the M3 Pro), for segmentation and embedding |
  | Provider | CPU |
- **"Numero di persone" (optional, [user] 2026-09-23).** This is new user-approved behaviour. When a
  transcription is started, the user **may** state how many people speak in the recording.
  - **Present, `k`:** the adapter passes `numClusters = k`, an exact count through `cutree_k`.
    `threshold` is ignored.
  - **Absent:** `numClusters = -1`, and automatic threshold clustering is used at 0.4.
  - **Where the value lives.** The value belongs to the `Elaborazione` it was given for. It is fixed
    when that `Elaborazione` is created, immutable, and persisted, so a queued or recovered run uses
    it. It crosses the `Diarizzatore` port as a nullable Published Language value. Ports carry no
    sherpa types (ADR 0004).
  - **Carrier and ACs.** The command and port that carry it, its UI, and its validation are folded
    into the manifest by `build-manifest`, from the architect's proposal returned with this ADR
    (see `features/trascrizione-con-parlanti/dispatch.log`, 2026-09-23). The manifest is
    authoritative for them.
  - **Bound: an integer from 1 to 10 [user] 2026-09-23.** The architect proposed 1..20, and the
    user chose 1..10. `NumeroPersone` is a value object in `:trascrizione:dominio`. Its factory
    rejects anything outside 1..10 with `NumeroPersoneFuoriIntervallo`, and the S2 presenter
    validates the field before it invokes the command.
  - **Where the field is offered, and no automatic start [user] 2026-09-23.** *(This resolves the
    open point this ADR first recorded: where to enter the count for recordings that R1 queued
    automatically on import.)* This ADR is the **single home** of the decision. ADR 0004 and ADR
    0012 amend their text to point here.
    - **No automatic transcription on import.** In R1 a newly imported `Registrazione` stays
      without an `Elaborazione`, shown as `NON_AVVIATA`. The policy "on `RegistrazioneAggiunta` →
      `AvviaElaborazione`" (tactical Q-6, ADR 0004 Decision/Execution, ADR 0012 Amendment R2) is
      **removed**.
    - **The user starts the transcription** from the S2 row with **"Trascrivi"**, and after a
      failure with **"Riprova"**. `AvviaElaborazione` therefore has one actor: the utente. It
      keeps its INV-4 checks.
    - **Both actions offer the optional "Numero di persone" field.** Empty means `numeroPersone`
      is absent, so clustering is automatic.
    - **"Riprova" prefills the field** with the `numeroPersone` of the failed `Elaborazione`, and
      leaves it empty if that run had none. The user may change or clear it, and the new
      `Elaborazione` stores the value that was submitted.
    - **Unchanged.** The serial FIFO queue (ADR 0004): a started `Elaborazione` is still created
      `in_attesa` and processed in order. `RegistrazioneAggiunta` is still published by Progetto,
      now with after-commit consumers only (view refresh).
- **Adapter behaviour contract.**
  - Seconds are rounded to ms.
  - Overlapping `Turno`s are emitted (per [INV-7]) and sorted by start.
  - `voceIndice` gaps are allowed.
  - Silence produces 0 `Turno`s, and the pipeline then applies AC-72.
  - The native call holds the Mutex for the whole phase, so option (b) of `attesa-mutex-estrazione`
    is impossible for this phase.
  - With `numClusters = k`, the result has **at most `k`** distinct `voceIndice`. It can have fewer:
    segments below `minDurationOn` are dropped. A `k` larger than the audio supports must not fail
    the `Elaborazione`. sherpa's behaviour when `k` exceeds the embedding count is **not measured**.
    The adapter's `@modelli` contract test must pin it, and if sherpa rejects that `k`, the adapter
    falls back to automatic clustering.

### `:modelli` catalogue entries (ADR 0008 (c))
| field | segmentation | embedding |
|---|---|---|
| `id` | `segmentazione-pyannote-3.0` (the example id already in `VoceCatalogo` KDoc) | `embedding-wespeaker-resnet34-lm` |
| `ruolo` | segmentazione | embedding (diarization) |
| `url` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx` (the `recongition` misspelling is in the upstream tag) |
| `formato` | `TAR_BZ2` | `FILE` |
| `sha256` (asset) | `24615ee884c897d9d2ba09bb4d30da6bb1b15e685065962db5b02e76e4996488`. Computed locally; none is published upstream. | `e9848563da86f263117134dfd7ad63c92355b37de492b55e325400c9d9c39012`. Computed locally, and **matches** the release `checksum.txt`. |
| `dimensioneByte` (asset) | `6958444` | `26530550` |
| file loaded inside `percorso(id)` | `model.int8.onnx` (1,540,506 B). `model.onnx` fp32 is present but unused. | `wespeaker_en_voxceleb_resnet34_LM.onnx` |
| `licenza` | MIT | CC-BY-4.0 |
| `attribuzione` | "pyannote segmentation-3.0 (MIT), © pyannote (Hervé Bredin); ONNX export by k2-fsa sherpa-onnx" | "WeSpeaker ResNet34-LM (CC-BY-4.0), WeSpeaker team; trained on VoxCeleb (CC-BY-4.0, Nagrani/Chung/Zisserman)" |

**Embedding reuse.** The same catalogue id is the **first** candidate for `EstrattoreImpronta`. The
two roles stay **independently configurable** in `:avvio`. The final word on the voice-print model
belongs to `impronta-vocale-affidabilita`. If the diarizer's embedding is swapped later, every stored
print goes stale (ADR 0012 (b)) whenever the two roles share the id.

### Rejected options
- **Music filtering before diarization/ASR (sherpa CED-mini audio tagging).** Rejected **[user]**.
  - It flagged 156 s (3.5 %), mostly blips under 2 s inside speech turns. Background music under
    talk is not separable this way.
  - With auto clustering it made the result worse: 12 clusters.
  - With `num_clusters = 4` it gave a less plausible split, including a 53 s cluster that may be a
    real speaker merged away.
- **Stricter VAD (Silero 0.6) before clustering.** Rejected: it gave 14 clusters.
- **Merging small clusters into the nearest, as a Kotlin post-filter.** Not adopted: it gave 5 on
  the music-filtered variant, and a stated count solves the case more directly. It remains a
  fallback.
- **Reverb diarization v1/v2.** Dropped for its non-commercial licence.
- **Alternative embeddings** (3D-Speaker CAM++, NeMo TitaNet-small), **fp32 segmentation**, and
  **CoreML.** Not measured. They remain tuning options.

## Consequences
- **Budget share.** Diarization took about 245 s per 75 min with auto clustering under load 15–79,
  and 67 s with `num_clusters = 4` under a different load. The load differed, so the two numbers
  are not comparable. Diarization and ASR together extrapolate to **≈ 462 s per 60 min ≤ 600 s**
  (ADR 0013), and peak RSS is about 2.15 GB. Idle-machine figures and a `debug = true` per-stage
  split are [hypothesis] until `benchmark-elaborazione` runs.
- **Boundary change.** `Diarizzatore.diarizza` gains a nullable `numeroPersone` parameter. The
  `Elaborazione` gains a persisted, nullable, immutable field, added by a forward-only migration
  (ADR 0006). `AvviaElaborazione` gains an optional input, and S2 gains the field. These are
  folded by `build-manifest`. The `porte-trascrizione`, `elaborazione`, `avvia-elaborazione` and
  `esegui-elaborazione` blocks are already built, so they need a rework.
- **No automatic start (2026-09-23 [user]).** The `abbonato-registrazione-aggiunta` block is
  dropped from the manifest. `RegistrazioneAggiunta` loses its synchronous consumer. The R1
  composition no longer registers it. A `Registrazione` may now exist in R1 with no
  `Elaborazione`; `NON_AVVIATA` + "Trascrivi" already covers that state, so no invariant changes.
  One cost is accepted: importing a batch now needs one "Trascrivi" per row.
- **Ubiquitous language.** `NumeroPersone`, shown as "Numero di persone", was added to
  `context-map.md` § Trascrizione on 2026-09-23 (optional, 1..10).
- **Accepted failure modes.** Without a count, long recordings **over-count**; `Revisione` →
  `unire` fixes that in one action. Short backchannels are dropped. Crosstalk is handled only up to
  2 overlapping speakers. Background music can create phantom small `Voce`s.
- **Residual gaps (the closure is on user acceptance of the evidence) [user].**
  1. The spike asked for ≥ 3 real `Registrazione`s. Only **2** were measured, both Italian with
     English jargon, and neither had real English turns.
  2. **No DER.** There are no reference labels. The error estimate is speaker counts, plus the
     user's ground truth of 4 speakers on Via Roquel, plus the user's reading of the transcript.
     Threshold 0.4 is a count-plausibility choice, not a DER optimum.
  3. The timings were taken under heavy load.
- **Re-measure trigger.** When a 3rd recording is available, ideally with English turns, re-run the
  auto and `num_clusters` variants and record the result as an amendment. Reopen via a superseding
  ADR, in this order: threshold tuning, then the small-cluster post-filter, then an out-of-process
  pyannote-community-1 adapter (the ADR 0004 fallback). Do this if either happens:
  - auto clustering keeps over-counting so much that users must always state the number;
  - a stated count gives visibly wrong splits.
- **Discursive.** No grep checks clustering values. The guardians are the `diarizzatore-sherpa`
  block (AC-249 contract test, AC-250 catalogue entry) and the new `numeroPersone` ACs.

## Amendment 2026-09-24 — `numClusters` above the real count
- **Measured** while building `diarizzatore-sherpa` (dispatch.log 2026-09-24, merge 41b70ff, real
  `@modelli` run). It closes the "not measured" point of the adapter behaviour contract above.
- **sherpa never throws** when `numClusters` is larger than the real number of speakers. The
  fallback to automatic clustering "if sherpa rejects that `k`" is therefore never triggered.
- **But the result is not monotonic in `k`.** A `k` above the real count can return **fewer**
  clusters than a smaller `k`. On a clip with 2 voices, `k = 10` returned **1** cluster. An
  over-stated count can merge distinct people into one `Voce`.
- **Consequence for the UI.** The "Numero di persone" hint must be the **real** count of people who
  speak, never an upper bound or a "safe" high number. The S2 field's hint must tell the user so;
  leaving it empty (automatic clustering) is the right choice when the count is unknown.
- The 1..10 bound and the absent → automatic rule are unchanged. The re-measure trigger above also
  covers this: the benchmark on a real recording should check `k` = the real count.

## Amendment 2026-09-24 (b) — "Ritrascrivi" also offers the field (pointer; [ADR 0018](0018-ritrascrivi.md) is its home)
- User decision 2026-09-24: a `completata` `Registrazione` can be transcribed again ("Ritrascrivi", a new
  `Elaborazione`, the Trascritto replaced only when it completes). The S2 row then offers the same optional
  "Numero di persone" field under the rules above (plain field, empty = automatic, 1..10, real count — never an
  upper bound), **prefilled with the latest `Elaborazione`'s value** exactly like "Riprova". ADR 0018 §4 owns
  the confirmation dialog and the S2 states during/after a re-run.

## Amendment 2026-09-24 (c) — diarization config and embedding superseded by [ADR 0019](0019-separazione-semi-automatica.md) (pointer; ADR 0019 is the home)
- **Superseded rows of the Decision table:**
  - Segmentation file: now `model.onnx` (fp32). The catalogue entry is unchanged.
  - Clustering: sherpa runs only as step 1, with an over-split `FastClustering` (`numClusters = -1`,
    `threshold = 0.2`) and **ResNet34-LM kept**. Its labels are discarded.
  - Voices now come from **TitaNet-small** piece embeddings (`embedding-nemo-titanet-small`, a new
    entry) and **our own average-linkage cosine AHC**, followed by nearest-centroid assignment
    (ADR 0019 §1.2). `threshold = 0.4` is gone; the auto cut is AHC distance 0.5.

  `windowShiftRatio`, the durations, the threads and CPU are unchanged.
- **Catalogue:** add `embedding-nemo-titanet-small`. `embedding-wespeaker-resnet34-lm` stays (step 1
  only). The segmentation entry stays, and its file used becomes `model.onnx`.
- **"Embedding reuse":** decided. TitaNet-small, not ResNet34-LM, is the `EstrattoreImpronta` model
  (ADR 0019 §2).
- **"Numero di persone"** rules are unchanged (optional, 1..10, the real count, stored on the
  `Elaborazione`). With the new k-cut, a `k` above the real count tends to **split** one voice
  instead of merging people (ADR 0019 §1.3).
- **"Adapter behaviour contract"**, the bullet "the native call holds the Mutex for the whole phase":
  now only step 1 is one hold, and each piece embedding is one hold (ADR 0019 §1.5).
- **Why.** The old setup is unstable: a 10 ms shift changes the split, and stability is 0.63
  (fix-batch-18). The new one scores 0.94. Even the stable setup is not correct on these voices
  [user listening check], hence the semi-automatic flow of ADR 0019 §3–§6.
