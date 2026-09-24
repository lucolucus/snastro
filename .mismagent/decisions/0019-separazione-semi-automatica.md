---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0014 (runtime config rows Segmentation file / Clustering, "Embedding reuse"); ADR 0017 §1.1 (diarizza no longer a single native call); ADR 0009 (transient per-Segmento embeddings); tactical INV-8 wording
closes_spike: null  # impronta-vocale-affidabilita is PARTIALLY answered (model chosen, §2); it stays open for SoglieFascia / BUDGET_IMPRONTA_MS / go-no-go
enforced_by:
  kind: presence+prohibition
  rule: "! grep -rn --include='*.kt' --exclude-dir=build 'model\\.int8\\.onnx' avvio/src/main trascrizione/adattatori/src/main && grep -q 'ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e' modelli/src/main/kotlin/snastro/modelli/CatalogoDiarizzazione.kt"
  exigible_from: "diarizzatore-sherpa"   # its 2026-09-24 (ADR 0019) rework; red on the tree today BY DESIGN (validated 2026-09-24 via bash -c: exit 1 — SelezioneAdattatoriMl.kt:82 still resolves model.int8.onnx and the TitaNet entry does not exist yet)
experiment: "scratchpad/diar2 of session 1f80eddf (outside the repo): common.py, clus.py, stage1-3.py, out/stage*.log — the settings below are read from it (2026-09-24)"
amended: 2026-09-24   # "Amendment 2026-09-24 (b) — the user's answers to the Points for the user" [user]: fallback references (whole named Voce), preview before apply (Applica/Annulla), incerte stay (confirmed), provisional Proposte shown (confirmed); §1.9 "Parametri misurati" copied from the experiment scripts
---
# 0019 — Semi-automatic voice separation: a stable diarization (seg-3.0 fp32 + TitaNet-small + our own clustering), user-named reference sentences, and "Riassegna per somiglianza"

## Context
**Evidence.** Sources are `features/trascrizione-con-parlanti/research/misure-r1-asr-diarizzazione.md`,
its last two sections, and `dispatch.log` (2026-09-24, fix-batch-18 and the `(r2) decision` lines).
- **The current diarization is unstable (fix-batch-18).** It uses ADR 0014: pyannote seg-3.0 int8,
  WeSpeaker ResNet34-LM, and sherpa `FastClustering` with `numClusters = k`. The app reproduces the
  spike exactly, so this is not an app bug. But a 10 ms shift of the audio changes the split from
  [1874, 737, 367, 349] s to [2094, 756, 470, 12] s. Voice centroids have cosine 0.93–0.99, so they
  barely separate. Stability over +10/+48/+500 ms shifts averages **0.63** (min 0.54).
- **A better setup was measured on the full Via Roquel recording (75 min, ≤ 6 threads).** It uses
  seg-3.0 **fp32**, NeMo **TitaNet-small** embeddings, and **our own agglomerative clustering**:
  average-linkage cosine on ~3 s pieces, cut at k while ignoring clusters < 60 s, then
  nearest-centroid assignment.
  - Stability is **0.940** (min 0.928), with about 100 s of wall time.
  - Four unrelated embedding models agree at 0.95 on the same labelling. TitaNet-L gives the same
    stability for more time.
  - The experiment reported **19 s** of TitaNet-S embedding time for the 75 min.
  - **What the experiment actually ran.** I read its scripts, which are outside the repo in the
    session scratch `diar2/`.
    - Step 1 is sherpa diarization with seg fp32 and **WeSpeaker ResNet34-LM**, `FastClustering`
      `numClusters = -1`, `threshold = 0.2` (over-split), wsr 0.5, on 0.3 / off 0.5. It took
      76–80 s per 75 min under load.
    - Step 1's segments are cut into ⌈d/3 s⌉ equal pieces.
    - Pieces ≥ 1.5 s go into an average-linkage cosine AHC.
    - The k-cut is raised until k clusters have ≥ 60 s.
    - Centroids are **weighted by piece duration**.
    - Every piece goes to its argmax centroid.
    - Stability is the mean and min of a frame-level (10 ms) Hungarian agreement over all pairs of
      {orig, +10, +48, +500 ms}.
  - **New Recording 4 at its real k = 2 is NOT stable with TitaNet-S: 0.721 (min 0.604)** [log].
    It is 0.924 at k = 3, which is the wrong count. The 0.94 figure holds for Via Roquel at k = 4
    only. This reinforces the semi-automatic flow below.
- **Stable is not correct (the user's listening check, 2026-09-24).**
  - Via Roquel: groups 1 and 2 are each one real person, groups 3 and 4 are the **same** person, and
    the 4th real speaker has no group of their own: they are mixed into the others.
  - New Recording 4 has **2** speakers, not the 3 the analysis suggested.
  - **Unsupervised clustering alone cannot separate these voices.**
- **The user approved a semi-automatic separation [user]:**
  1. the app separates automatically, with the better setup;
  2. the user names 1–2 reference sentences per person in S3;
  3. **"Riassegna per somiglianza"** puts every sentence with the named person whose reference
     voice is most similar (supervised nearest-reference).

**Constraints this ADR must respect.**
- The Revisione model is [INV-6..12] and ADR 0012. Invariant-carrying Parlanti policies run
  **in the Revisione's transaction**, `RiallineaImpronte` and the `Documento` run **after commit**,
  and no ML runs inside a transaction.
- The native Mutex rules of ADR 0017 apply.
- Biometric data is handled per ADR 0009.
- Trascritto generations are handled per ADR 0018.
- The context edges of `architecture.md` apply. Trascrizione never knows names. Parlanti reads
  Trascrizione through consumer-owned ports and never commands it.

## Decision

### 1. Diarization (amends ADR 0014's runtime config and catalogue; ADR 0014's "Numero di persone" rules stand)

#### 1.1 Models
| Role | Model | Catalogue id | File loaded inside `percorso(id)` |
|---|---|---|---|
| Segmentation (step 1) | pyannote segmentation-3.0, **fp32** | `segmentazione-pyannote-3.0` (**unchanged**: same asset, same SHA) | **`model.onnx`** (5,992,913 B). Before this ADR it was `model.int8.onnx`. |
| Step-1 embedding (sherpa's internal over-split clustering only) | WeSpeaker ResNet34-LM | `embedding-wespeaker-resnet34-lm` (**unchanged, kept**) | `wespeaker_en_voxceleb_resnet34_LM.onnx` |
| Piece embedding (step 3) **and** `ImprontaVocale` (§2) | NVIDIA NeMo **TitaNet-small** | **`embedding-nemo-titanet-small`** (new id) | `nemo_en_titanet_small.onnx` |

ResNet34-LM **stays** because the measured step 1 used it. Only the step-1 segment boundaries
depend on it. Using TitaNet-S in step 1 too would drop one model and one download, but it was not
measured. It is a later option, gated by AC-488/489 re-run, and needs an amendment.

#### 1.2 Pipeline of `Diarizzatore.diarizza(c, numeroPersone)`
The port stays exactly as it is (`tec-diarizzatore`: `List<Turno>`, at most k distinct `voceIndice`,
never persisted). Only the adapter changes.
1. **Step 1: speech regions and local speaker changes (native, sherpa).** Run
   `OfflineSpeakerDiarization` with seg-3.0 fp32 and ResNet34-LM, `FastClustering` with
   `numClusters = -1` and **`threshold = 0.2`** (`SOGLIA_PASSO_1`, an over-split), plus
   `windowShiftRatio = 0.5`, `minDurationOn = 0.3`, `minDurationOff = 0.5`. These are exactly the
   experiment's `stage1.py` settings.
   - Its **cluster labels are discarded**. We keep only its segments, which carry the speech
     regions, the overlaps, and the local speaker-change boundaries found by segmentation.
   - The over-split ensures that a speaker change inside continuous speech still ends a segment.
     It gave about 1 550 segments on 75 min.
2. **Step 2: pieces.** Cut each step-1 segment into ⌈d / `PEZZO_MS`⌉ equal pieces, with
   `PEZZO_MS = 3000`, so every piece is ≤ 3 s. A piece never crosses a step-1 boundary. When
   step-1 segments overlap, both keep their pieces ([INV-7]: nothing is trimmed).
3. **Step 3: one embedding per piece** with TitaNet-small, L2-normalized, through the
   `:ml-sherpa` embedding wrapper (§1.4).
4. **Step 4: our own agglomerative clustering**, pure Kotlin.
   - Linkage is average linkage on cosine distance (1 − cos), over the pieces ≥
     `DURATA_MINIMA_PEZZO_AHC_MS` (**1500 ms**, the experiment's `MINP`).
   - A cluster **qualifies** when its speech (summed over pieces ≥ 1.5 s) is ≥
     `min(DURATA_MINIMA_CLUSTER_MS = 60 000, QUOTA_MINIMA_CLUSTER = 10 % × total speech)`.
     - The experiment used a fixed 60 s.
     - The 10 % cap is my addition, so a short clip still has a qualifying cluster. It only changes
       anything below 10 min of speech, which is unmeasured.
   - **With `numeroPersone = k`:** cut the dendrogram at m = k, k+1, … clusters, and stop at the
     first m where at least k clusters qualify. Keep the k with the most speech.
     - If no m ever reaches k qualifying clusters, the audio holds fewer voices. Keep the qualifying
       clusters at the first m where their count is highest, so the result has **< k** voices. That
       still satisfies the port's "at most k".
     - If nothing qualifies, keep one cluster with all the speech.
   - **Without it:** cut at the cosine distance **`SOGLIA_AHC_AUTO = 0.5`**, then keep the
     qualifying clusters, or the largest one if none qualifies. The experiment's sweep over
     0.3–0.8 (TitaNet-S, seg fp32) counted clusters of ≥ 60 s:
     - Via Roquel (4 real speakers) gave 4 at 0.4–0.6;
     - NR4 (2 real speakers) gave 1 at 0.4, 3 at 0.5, and 5 at 0.6.

     No value is right for both. 0.5 gives 4 and 3: exact on one, and **one too many** on the
     other. An over-count is the cheaper error, since `unire` and §4 repair it.
   - Every tie breaks on the lowest index. Input order is time order, so the output is deterministic.
5. **Step 5: nearest-centroid assignment.** A centroid is the kept cluster's normalized embeddings,
   **weighted by piece duration** and summed, then L2-normalized (the experiment's `centroids`). **Every** piece goes to its nearest centroid, including short pieces and
   pieces of clusters that did not qualify. Its `voceIndice` is that centroid's index. Each piece is
   one `Turno`, and the `Allineatore` (ADR 0015, unchanged) merges consecutive same-voice pieces.

**Large inputs.** Average linkage needs O(n²) memory in the number of pieces: about 1 200–1 500
pieces per hour, and about 9 MB for 1 500 pieces as `FloatArray`. Above `PEZZI_MASSIMI_AHC = 6000`
(about 5 h of audio), step 4 runs on a deterministic subsample of every ⌈n/6000⌉-th piece, and
step 5 still assigns every piece.

#### 1.3 "Numero di persone" under the new clustering
ADR 0014's rules are unchanged: optional, 1..10, empty means automatic, "the **real** count, never
an upper bound", and stored on the `Elaborazione`. What changes is the failure mode. With sherpa, a
`k` above the real count could **merge** people (ADR 0014 Amendment 2026-09-24). With our k-cut, it
tends to **split** one real voice into two qualifying clusters, as it did on NR4 with k = 3. That is
cheaper to repair: `unire`, or §4. The S2 hint text stays as it is.
**At the real k the result can still be unstable.** NR4 at k = 2 scored 0.72 (§Context). The stated
count fixes the number of Voci, not their correctness. §3–§6 exist for that.

#### 1.4 Where it lives
- **`:ml-sherpa`** (native, confined by ADR 0004):
  - the step-1 call (`SessioneSherpa.diarizza` + `ConfigDiarizzazione`, unchanged in shape);
  - a new embedding wrapper, **`EmbeddingSherpa`**, over `SpeakerEmbeddingExtractor`:
    - it computes the embedding of one sample array per call, with **one `conSessione` per call**;
    - the **model is loaded once and cached across calls**, which is the `RiconoscitoreSherpa`
      pattern (AC-388/410);
    - `chiudi()` releases it.

  Both `diarizzatore-sherpa` (per piece) and `estrattore-impronta-sherpa` (per print, §2) use this
  one implementation, so one context's embeddings are computed exactly like the other's. Each
  adapter owns its own instance and lifecycle:
  - the diarizer releases its instance at the end of the `Elaborazione`, like the recognizer;
  - the extractor releases its instance when the project closes.
- **`:trascrizione:adattatori` (`..ml`):** `DiarizzatoreSherpa` orchestrates steps 1–5. The
  clustering (steps 2, 4, 5) is **pure Kotlin**, `internal`, and fully tested in the gate on
  synthetic embeddings, with no natives. No `com.k2fsa` type leaves `:ml-sherpa`.
- **`:avvio`** (`SelezioneAdattatoriMl`, R1 wiring) passes three files to `DiarizzatoreSherpa`:
  segmentation `model.onnx`, ResNet34-LM for step 1, and TitaNet-S for the pieces. The catalogue
  list **adds** TitaNet-S; ResNet34-LM and segmentation stay.

#### 1.5 The native Mutex (amends ADR 0017 §1.1: `diarizza` is no longer "a single native call by nature")
- Step 1 is **one** hold. It is a single native call and cannot be interrupted, as before.
- Step 3 takes **one hold per piece**, with the model cached. This is the §1.1 rule that ASR
  already follows.
- Steps 2, 4 and 5 run with the Mutex **free**.

The longest wait an extraction can meet during diarization therefore shrinks from "the whole
diarization" to "step 1". The experiment's `stage1.log` measured step 1 at **76–80 s per 75 min**
under load, which is ≈ 60–64 s per hour. It is still a single hold that cannot be interrupted.
`benchmark-elaborazione` prints the idle figure (AC-542). ADR 0017's visible, cancellable wait on
S3 is unchanged.

#### 1.6 Time budget (ADR 0011: 60 min in ≤ 600 s on the M3 Pro)
- **Diarization** measured about 100 s per 75 min, which is **≈ 80 s per hour**:
  - step 1 took 76–80 s;
  - the TitaNet-S piece embeddings took 19 s;
  - the AHC took the rest.

  The old setup took 245 s per 75 min with auto clustering. Both figures are under load.
- **Whole pipeline:** re-using ADR 0013's full-file run, (578.5 − 244.7 + 100) s per 4511 s gives
  **≈ 346 s per hour**, down from ≈ 462 s. That leaves ≈ 42 % headroom.
- The fp32 segmentation costs roughly 10–20 % more than int8, and it is already inside the 100 s.
- The AHC is about 1 500² distance entries, well under a second.
- Idle-machine figures remain [hypothesis] until `benchmark-elaborazione` runs.

#### 1.7 `:modelli` catalogue entry (ADR 0008 (c)) — `embedding-nemo-titanet-small`
| field | value |
|---|---|
| `id` | `embedding-nemo-titanet-small` |
| `ruolo` | embedding (diarizzazione + impronta vocale) |
| `url` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/nemo_en_titanet_small.onnx` (the `recongition` misspelling is in the upstream tag) |
| `formato` | `FILE` |
| `sha256` (asset) | `ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e`. It is in the release `checksum.txt`, and was **verified on the downloaded bytes on 2026-09-24**. |
| `dimensioneByte` | `40257283` (verified 2026-09-24) |
| file inside `percorso(id)` | `nemo_en_titanet_small.onnx` |
| `licenza` | CC-BY-4.0 |
| `attribuzione` | "NVIDIA NeMo TitaNet-small (CC-BY-4.0), ONNX export by k2-fsa sherpa-onnx" |

The segmentation entry is **unchanged**: same id, URL, SHA, size, licence and attribution. Only its
row "file loaded" becomes `model.onnx`. The asset bytes did not change, so no new id is minted
(ADR 0008 (c)). `embedding-wespeaker-resnet34-lm` is unchanged and kept (step 1). Users with R1
models already installed download only the new 40 MB entry.

#### 1.8 What the build must reproduce
The experiment is in the session scratch `diar2/`, outside the repo: `common.py`, `clus.py`,
`stage1.py`, `stage2.py`, and `out/stage*.log`. **The orchestrator should hand these files to the
`diarizzatore-sherpa` worker as its reference.** They are not committed, because they hold scratch
paths and derived data.

Two opt-in `@modelli` ACs gate the acceptance:
- **Stability.** Via Roquel at k = 4 is run on {orig, +10, +48, +500 ms} (leading silence). The
  frame-level (10 ms) Hungarian agreement over all pairs must give mean ≥ 0.92 and min ≥ 0.90. The
  experiment measured 0.940 / 0.928.
- **Reproduction.**
  - Via Roquel at k = 4 gives 4 voices whose speech is within ±15 % of 1136, 971, 803 and 584 s.
  - Auto at `SOGLIA_AHC_AUTO = 0.5` gives 4 voices on Via Roquel and 3 on NR4.
  - The test also **prints** NR4's stability at k = 2 (measured 0.72). This is information, not a
    pass condition.

If either AC cannot pass, the block stops and returns to the architect. It must never ship a
"close enough" clustering silently.

#### 1.9 Parametri misurati (added 2026-09-24 (b); copied from the experiment scripts, code logic only)
Read from `diar2/{common,stage1,stage2,stage3,clus,best,aid}.py`. `best.py` and `aid.py` only produce
the listening aid and the transcript for the user; they set no parameter. `stage3.py` is the
**baseline** (sherpa `FastClustering` with `numClusters = k`, seg int8), scored with the same stability
method. Where this ADR's §1.2 goes beyond the scripts, the row says so.

| What | Exact experiment logic | Where |
|---|---|---|
| Audio in | 16 kHz mono, `int16 / 32768.0` as float32. The shifted variants **prepend zeros**: 0, 160, 766 and 8000 samples (orig, +10, **+47.9**, +500 ms). | `common.audio`, `SHIFTS` |
| Step 1 (segments only) | `OfflineSpeakerDiarizationConfig`: pyannote segmentation **`model.onnx`** (fp32; the int8 file was run too, for comparison), `window_shift_ratio = 0.5`, `num_threads = 6`; embedding **`wespeaker_en_voxceleb_resnet34_LM.onnx`**, `num_threads = 6`; `FastClusteringConfig(num_clusters = -1, threshold = 0.2)`; `min_duration_on = 0.3`, `min_duration_off = 0.5`. The segments come from `process(a).sort_by_start_time()` as (start, end, speaker) in seconds. **The speaker label is never used downstream.** | `stage1.py` → `common.diarize(…, 'r34', nc=-1, th=0.2, seg=…)` |
| Pieces | `W = 3.0` s. A segment [s, e) gives `n = max(1, ceil((e − s) / W))` equal pieces, `[s + (e−s)·j/n, s + (e−s)·(j+1)/n)` for j = 0..n−1. **Overlap between pieces = 0.** Pieces never cross a segment boundary. The pieces of overlapping segments are all kept. Piece order is segment order, then j. | `clus.pieces` |
| Piece embedding | Samples `a[int(s·16000) : int(e·16000)]`, one stream per piece (`create_stream`, `accept_waveform(16000, x)`, `input_finished`, `compute`), TitaNet-small `nemo_en_titanet_small.onnx`, `num_threads = 6`; the extractor is created once per model and reused. **Normalisation: `v / (‖v‖₂ + 1e-9)`.** | `common.embed`, `common.extractor` |
| AHC input | Only pieces with duration **≥ `MINP = 1.5` s** (`fit`). | `clus.ahc_labels` |
| Linkage | Distance matrix `D = 1 − X·Xᵀ` (cosine distance on the normalised embeddings), diagonal = +∞. Repeat n−1 times: take `argmin` of D over the flattened matrix (row-major, so a tie goes to the lowest i and then the lowest j; i < j after a swap) and record the merge (id_i, id_j, d). The new row is `(D[i]·size[i] + D[j]·size[j]) / (size[i] + size[j])` = **average linkage (UPGMA) weighted by piece COUNT**. The duration vector `w` is passed to `ahc` but **not used**. Then D[i][i] = +∞, row and column j = +∞, size[i] += size[j], and cluster i takes the new id n + t. | `clus.ahc` |
| Cut | With a count m: apply the first n − m merges (union-find). With a threshold: apply the merges with `d ≤ thr` (a prefix, since average-linkage heights are monotone). Labels are renumbered by first appearance in piece order. | `clus.cut` |
| Min-cluster rule, k given | `kk = k`; loop: cut at kk clusters; `du[c]` = summed duration of the **fit** pieces in c; `big = {c : du[c] ≥ 60.0 s}`; stop when `len(big) ≥ k` or `kk ≥ n`, else kk += 1. Keep the **k clusters with the largest du** (sorted descending). | `clus.ahc_labels(k=…)` |
| Min-cluster rule, auto | Cut at `thr`; `big = {c : du[c] ≥ 60 s}`, or the single largest cluster if none. The stage-2 sweep ran thr ∈ {0.3, …, 0.8} and counted distinct labels **after** assignment. | `clus.ahc_labels(thr=…)`, `stage2.py` |
| Centroids | `C_c = Σ_{fit i ∈ c} w_i · x_i`, **weighted by piece duration**, then L2-normalised (no epsilon). | `clus.centroids` |
| Assignment | `lab = argmax(E · Cᵀ)` over **all** pieces (short ones included); a tie goes to the lowest centroid index. Output: one (piece start, piece end, lab) per piece. | `clus.ahc_labels` |
| Stability | For each of the **6 pairs** of {orig, s10, s48, s500} (`itertools.combinations`), with offset o = shift samples / 16000: 10 ms frames, `L[round((s − o)·100) : round((e − o)·100)] = label` (clipped at 0; unlabelled = −1; **a later segment overwrites an earlier one** where they overlap), with n = max end·100 + 300 frames. Mask = the frames labelled in **both**. Build the confusion matrix, take the **maximum-weight one-to-one mapping** (Hungarian, padded to a square, cost = max − M), and agreement = matched frames / masked frames. Stability = **(mean, min)** over the 6 pairs. | `common.frames`, `hung`, `agree2`, `stability` |
| Diagnostic only | `separation`: centroids of the labels with ≥ 30 s of fit pieces; the mean and max inter-centroid cosine; the mean piece-to-own-centroid cosine. Not a pass condition. | `clus.separation` |

**§1.2 additions not in the scripts, all unmeasured:**
- the qualifying quota `min(60 s, 10 % of speech)`, where the scripts use a fixed 60 s;
- the "< k voices" rule and the "nothing qualifies → one cluster" rule when k is given. The script's
  loop runs up to `kk = n`, where no cluster can qualify and `centroids` would get an empty set;
- the `PEZZI_MASSIMI_AHC` subsample.

The worker reproduces the table exactly (AC-484/485), plus these additions.

### 2. One embedding model: TitaNet-small is also the `ImprontaVocale` model
- The **`EstrattoreImpronta` uses `embedding-nemo-titanet-small`**, with the same catalogue entry
  and the same downloaded copy as the diarizer. This takes ADR 0014's "Embedding reuse" path with
  the new model.
  - `EstrattoreImpronta.modello = "embedding-nemo-titanet-small"`.
  - The two roles stay independently configurable in `:avvio`. If the id ever changes, every print
    goes stale and is re-derived (ADR 0012 (b)).
- **Spike `impronta-vocale-affidabilita` is PARTIALLY answered.** *Which model* is decided here,
  catalogue entry included. The choice rests on the four-model agreement at 0.95 and on TitaNet-S
  being the fastest of the stable ones. TitaNet-L gave the same stability for more time. Still open:
  - calibrated `SoglieFascia` (forte/debole);
  - the false-"forte" rate and the go/no-go for automatic `Proposta`s;
  - the `BUDGET_IMPRONTA_MS` calibration;
  - the ≥ 3-recording, ≥ 2-recurring-people measurement.

  §4 gives a first **within-recording** same/different-person distribution for free: the user's
  reference sentences are labelled data. It is not the cross-session evidence the spike asks for.
- **`estrattore-impronta-sherpa` is unblocked.** Its gate was the model choice, the catalogue
  entry and the model id: that part of the gate is **satisfied by this ADR**. The thresholds stay
  **provisional configuration** (ADR 0004: `SoglieFascia` is injected). They are not a build gate.
  Once the extractor is wired, R2's REALI composition turns the Proposta on (`proposte = true`),
  with provisional Fasce (see "Points for the user").
- **Existing print rows** were written by the R2 stand-in `EstrattoreImprontaAssente` (model
  `nessun-estrattore`), so they are stale for the new model. `RiallineaTutteLeImpronte` re-derives
  them at the next project open (AC-316). No migration is needed (ADR 0012 (b) point 3).

### 3. Reference sentences: a **confirmed `Segmento`** (Trascrizione) is a **frase di riferimento** (Parlanti)
To be supervised, the similarity needs sentences **the user has vouched for**. The Voce a person is
attributed to is not good enough: diarization mixed people into it, and that is the problem we are
solving.
- **Trascrizione: `Segmento.confermato: Boolean`.**
  - It means "the user has placed or confirmed this Segmento on its current Voce by an explicit act".
    It carries no name.
  - It is persisted as `segmento.confermato`, added by forward-only migration **`4.sqm`**
    (`ALTER TABLE segmento ADD COLUMN confermato INTEGER NOT NULL DEFAULT 0 CHECK (confermato IN
    (0, 1));`, schema 4 → 5, ADR 0006 (a)). Existing rows get 0.
  - **Set to true** by:
    - a manual `RiassegnaSegmento`, on the moved Segmento;
    - `DividiVoce`, on the moved subset S;
    - the new `ConfermaSegmento(registrazioneId, segmentoId, confermato = true)`.
  - **Set to false** only by `ConfermaSegmento(…, false)`, which is "Togli conferma".
  - **Unchanged** by `UnisciVoci` and by the automatic `RiassegnaSegmenti` (§4).
  - A Ritrascrivi generation starts with every flag at 0. `Trascritto.crea` creates them that way,
    and ADR 0018 already warns that corrections are lost.
- **Why manual moves confirm too.** A user's hand-made correction must never be undone by a later
  automatic pass. This is the one rule that makes re-running §4 safe.
- **New invariant [INV-26]** (Trascritto aggregate):
  > [INV-26] a `Segmento` is `confermato` iff an explicit user act placed or confirmed it on its
  > current `Voce` (manual `riassegnare`, the moved subset of `dividere`, `ConfermaSegmento(true)`),
  > and no `ConfermaSegmento(false)` has revoked it since. `unire` keeps every flag.
  > `riassegnaInBlocco` (§4) never moves a `confermato` `Segmento`, never creates a `Voce`, and
  > never changes a flag. `crea` starts every flag at false.
- **[INV-8] reworded:** the set of `Segmento`s (ids, intervals, text) is identical before and after
  any `Revisione`. Only their `Voce` and their `confermato` flag may change.
- **Parlanti meaning.** A **frase di riferimento** of Parlante P in a Registrazione is a `confermato`
  Segmento of ≥ 1 000 ms on a Voce attributed to P, where P is **`attivo`**. Nothing new is stored in
  Parlanti: it is derived live from the Voci and the Attribuzioni. *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: without a confirmed sentence, P falls back to every Segmento ≥ 1 s of its Voci)*
  - A tombstone (`eliminato`) never has references. ADR 0009's erasure right means the app does not
    re-process that person's voice.
  - An `occasionale` may have references.
- **New event** `SegmentoConfermato(registrazioneId, segmentoId, confermato) : EventoPubblicato` on
  boundary `eventi-revisione`. Its only consumer is the after-commit view refresh
  (`Cambiamento(registrazioneId)`). The `Documento` does not render the flag, and no Parlanti
  policy needs it.

### 4. "Riassegna per somiglianza"

#### 4.1 Shape: a Parlanti **plan** + a Trascrizione **batch command**, glued in `:avvio`
It is **not one command**. A single command would have to live in one context:
- In **Trascrizione**, it would need names and prints, which Trascrizione never knows.
- In **Parlanti**, Parlanti would have to *command* a Revisione. That reverses the Customer/Supplier
  relationship of the context map, and `:parlanti:applicazione` cannot reach
  `:trascrizione:applicazione` anyway.

So the action has three parts. This is the same split as **Proposta di unione → `UnisciVoci`**,
which is already in the model.
1. **`PianoRiassegnazione`** is a Parlanti **read-model** (`:parlanti:applicazione ..letture`). It is
   computed, never stored, and never writes. It reads:
   - the Voci and their Segmenti through the consumer-owned `LettoreVoci`, which gains a
     `segmenti(id)` method (below);
   - the Attribuzioni and Parlanti through its own repositories.

   It embeds the needed Segmenti through `DecodificatoreAudio` + `EstrattoreImpronta`, outside any
   transaction, and classifies them through a new pure port **`ClassificatoreSomiglianza`**. Its
   output carries **no similarity number**:
   - `spostamenti: List<(segmentoId, da, a, intervallo)>`;
   - `incerte: Int`.
2. **`RiassegnaSegmenti(registrazioneId, spostamenti)`** is a Trascrizione Revisione command
   (`:trascrizione:applicazione ..comandi`). It is structural only and runs in **ONE** transaction.
3. **The glue** is the `:avvio` (R2) implementation of a `:ui`-declared action port. It runs
   `calcola`, then `RiassegnaSegmenti` if the plan is not empty, then reports the summary. It runs
   in the **per-project scope** of ADR 0017 §3, and holds no domain logic. *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).2: `calcola` ends in a preview; `RiassegnaSegmenti` runs only on Applica, with the held plan)*

Consumer-driven read pins (Parlanti is the consumer):
- `LettoreVoci.segmenti(id: RegistrazioneId): List<SegmentoDiVoce>?`. It returns `null` iff there is
  no Trascritto.
- `SegmentoDiVoce(segmentoId, voceId, intervallo, confermato)`, ordered by (`inizio`, `segmentoId`).
  It **never carries text**.

It is implemented by `lettore-voci-da-trascrizione` over the Trascrizione read API. The existing
`voci(id)` is unchanged.

#### 4.2 References and the set of Segmenti that may move
- **Reference Parlanti:** the `attivo` Parlanti with ≥ 1 frase di riferimento (§3) in this
  Registrazione. **At least 2 are needed.** Otherwise the plan returns
  `Errore(RiferimentiInsufficienti)` without any extraction.
- **References are explicit only.** *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: REVERSED — the fallback to the whole named Voce applies)* Segmenti that merely sit in an attributed Voce are **not**
  references. The initial diarization mixed people into those Voci, so using them would bring the
  contamination back. The alternative (implicit fallback) is under "Points for the user".
- **Target Voce of P:** the **lowest `voceId`** among the Voci attributed to P in this Registrazione.
  This rule is simple and deterministic, and the S3 naming action (§5) uses the same one.
- **Movable Segmenti:** every Segmento that meets both conditions:
  - it is **not** `confermato`;
  - its Voce is **unattributed**, or attributed to a **reference** Parlante.

  Segmenti on a Voce attributed to a Parlante **without references** are **frozen** *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: a named Voce without a confirmed sentence is no longer frozen)*: never
  extracted, never moved, never a target. This covers a Voce named only through its card, a Voce
  whose person has only sub-second references, and a tombstone. The app has nothing to compare
  them with, and the user named that Voce. The panel lists those names as "non toccate" (§6).
- **Movable Segmenti shorter than 1 000 ms** are neither extracted nor moved. They count as
  **incerte**, because a sub-second embedding is not reliable enough to override the diarizer.

#### 4.3 Similarity and confidence (pure adapter `classificatore-somiglianza`, `:parlanti:adattatori ..ml`)
- **Embedding of a Segmento:** `estrai` over `DecodificatoreAudio.campioni(SorgenteImpronta.di(listOf(intervallo)).intervalli)`.
  This reuses ADR 0012 (b)'s pure selection, so a Segmento contributes at most `BUDGET_IMPRONTA_MS`,
  trimmed from its start.
- **Score:** `score(s, P)` = cosine between s and **P's centroid**. The centroid is the normalized
  mean of P's normalized reference embeddings, so a single reference is used as it is.
  - A centroid, not the max over the references. With 1–2 references the two are the same or
    close. A centroid is less sensitive to one short or noisy reference, and it is the same
    operation as step 5 of §1.2.
  - The max is the first alternative for the calibration.
- **Decision.** Let `best` and `second` be the two highest scores.
  - **Sicura(best)** iff `best ≥ SIMILARITA_MINIMA` **and** `best − second ≥ MARGINE_MINIMO`.
  - Otherwise **Incerta**. That includes ties, an unknown fifth voice, music, and a print that
    cannot be compared (zero, NaN, or a dimension mismatch). It never throws.
- **Values: PROVISIONAL [hypothesis], `SIMILARITA_MINIMA = 0.30`, `MARGINE_MINIMO = 0.05`.**
  - They are injected as `SoglieSomiglianza`, and each is defined once. There is no user setting.
  - Calibration: run on Via Roquel with the user's references, report the moved and uncertain
    counts, and have the user listen. Record the outcome as an amendment.
  - The numbers stay inside the adapter, as with `Fascia` ([INV-20]).

#### 4.4 From classification to moves
For each movable Segmento classified **Sicura(P)**:
- If it is already on P's target Voce, nothing happens.
- Otherwise it gets a **spostamento** (`segmentoId`, `da` = its Voce, `a` = P's target Voce,
  `intervallo`).
- **Incerta** → it **stays where it is** and counts in `incerte`.

**Why incerte stay where they are, and there is no "incerto" bucket.** A bucket would be a new
Voce. It would pull uncertain sentences out of named Voci that the diarizer had probably got right,
and every re-run would churn Voci. Staying put is conservative and idempotent. The unnamed Voci
that remain after a run naturally hold the sentences still to check.

**Unnamed Voci** lose their confidently-classified Segmenti. A Voce left empty is removed by
[INV-6]. It has no Attribuzione, so the Parlanti policy has nothing to purge. Its label number is
never reused ([INV-12]).

**A reference Parlante's Voce never empties.** Its references are `confermato`, so they never move.
The automatic pass therefore never removes an `Attribuzione`. *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: false under the fallback; replaced by the guard of INV-27 (reworded))*

**New invariant [INV-27]** (Parlanti, `piano-riassegnazione` read-model) *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: INV-27 REWORDED there)*:
> [INV-27] a `PianoRiassegnazione` moves only a `Segmento` that meets all of these:
> - it is not `confermato`, is ≥ 1 000 ms, and lies on an unattributed Voce or on a Voce of a
>   reference `Parlante`;
> - it is classified Sicura(P) for a reference `Parlante` P (≥ 2 of them, `attivo`, each with ≥ 1
>   frase di riferimento);
> - it is not already on P's target Voce (the lowest `voceId` attributed to P).
>
> It moves the Segmento to that target Voce. It writes nothing, keeps no embedding after it
> returns, and exposes no similarity number.

#### 4.5 `RiassegnaSegmenti` (Trascrizione) — one transaction, all or nothing
- Aggregate: `Trascritto.riassegnaInBlocco(spostamenti): Esito<List<SegmentoRiassegnato>>`. It
  applies the moves in list order, **without** creating a Voce or touching a flag ([INV-26]) *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: validation against the pre-batch state, emptied Voci removed at the END of the batch)*. The
  whole batch is refused, with the state unchanged, in these cases:
  - **The plan is stale** → new error **`TrascrittoCambiato(registrazioneId)`**. That is any of:
    - a Segmento is missing, is no longer on `da`, or its interval ≠ `intervallo` (the interval
      also guards against a generation swap by Ritrascrivi, ADR 0018);
    - `a` no longer exists;
    - a Segmento is `confermato`.
  - `a == da`, or a duplicated `segmentoId` → the existing `RiassegnazioneNonAmmessa`.
- Service: one `inTransazione`. It does `trova` → `riassegnaInBlocco` → **one** `salva` → publish
  the N `SegmentoRiassegnato` in order.
  - `aNuova` is always false.
  - `daRimossa` is true on the move that empties its `da`.
  - An empty list → `Ok` with no write and no event.
- **Every existing consumer works unchanged.** `SegmentoRiassegnato` already exists, and `aNuova` is
  always false, so the case where a new Voce needs its own Proposta never arises.
  - The Parlanti revisione-policy runs **synchronously, per event, in the same transaction**, with
    [INV-21]/[INV-25] unchanged. Its `Esito.Errore` rolls back the **whole** batch (ADR 0012).
  - After commit, `Rigenerazione` and `RiallineaImpronte` are **coalesced per `registrazioneId`**:
    one regeneration and one re-alignment, not N. The prints of the attributed target Voci become
    stale and are re-derived after commit.
- **ErroreTrascrizione sweep.** Adding `TrascrittoCambiato` changes a sealed hierarchy that `:ui`
  `MessaggiErrore` matches exhaustively. As in ADR 0018 Amendment (b) §4, it is **one sweep**,
  owned by the `trascritto` rework, that includes the `MessaggiErrore` text.

#### 4.6 Idempotence and undo
- **Idempotent** with unchanged inputs *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: only when every reference Parlante has confirmed sentences)*:
  - the references are `confermato`, so they are fixed;
  - the centroids do not change;
  - the embeddings are deterministic on CPU;
  - moved Segmenti do not become references.

  A second run therefore plans **zero** moves and sends no command.
- It is **not a convergence loop.** More references, or new ones, give a different (better) plan.
- **No undo command** in v1:
  - the automatic pass never touches a `confermato` Segmento, never touches a frozen Voce, and
    never removes an `Attribuzione`;
  - what it may remove are unnamed Voci it emptied, and their label numbers are never reused.

  To correct a result, the user moves a sentence by hand, which also confirms it, adds a reference,
  and re-runs. The alternatives (a preview before applying, or an inverse batch) are under
  "Points for the user". *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).2: the PREVIEW was chosen; still no undo command)*

#### 4.7 Cost and the Mutex
- **One `estrai` per Segmento** to embed: references plus movable Segmenti ≥ 1 s. That is about
  **1 000 per hour** of audio.
  - **Estimate.** TitaNet-S took 19 s to embed about 75 min of pieces, so the native compute is
    about **15–20 s per hour**. On top of that come about 1 000 short WAV reads, and the model is
    loaded **once** (§1.4). The total is **≈ 20–40 s per hour on an idle M3 Pro [hypothesis]**. An
    opt-in `@modelli` AC measures it, with a hard ceiling of 60 s per 1 000 extractions.
- **Mutex: one extraction per hold. ADR 0017 §1.2 is unchanged.** Batching several Segmenti per
  hold was rejected:
  - one hold costs microseconds against 15–30 ms of embedding, so batching saves nothing;
  - batching would lengthen every pipeline wait, and §1.2 exists to prevent that.

  With a transcription of **another** Registrazione running, each extraction waits:
  - at most one ASR/VAD call, or one diarization piece-embedding (§1.5);
  - or, during step 1, the rest of step 1.

  The run slows down but never blocks the database: extraction happens outside any transaction,
  and only the final `RiassegnaSegmenti` transaction is short and local.
- **Cancellation** follows ADR 0017 §1.4–1.5. An interrupt during a wait or an extraction gives an
  `InterruptedException`, no plan, and nothing written. The final transaction is short and cannot
  be cancelled.

#### 4.8 Privacy (extends ADR 0009)
Per-Segmento embeddings, reference centroids and the diarizer's piece embeddings are **transient,
in memory only**. They are dropped when the computation returns, and are never cached, logged or
persisted, like the `Proposta` embedding (ADR 0009 Amendment (b)). A re-run re-extracts. No
tombstone's voice is processed (§3).

### 5. "Dai un nome a una frase" (the reference-naming action; composes existing commands)
One Segmento is selected, and the user picks "questa è P". P is an `attivo` Parlante of the
Progetto, or "nuovo…" with a Nome plus ricorrente/occasionale, as in Q-7. The S3 presenter picks the
case with a pure, unit-tested decision. The per-project executor (`avvio-parlanti`) then runs the
steps in order.

| Case | Steps |
|---|---|
| (a) the Segmento is already on a Voce attributed to P | `ConfermaSegmento(true)` |
| (b) the Segmento is the **only** one of its Voce | `ConfermaAttribuzione(that Voce, P)`, then `ConfermaSegmento(true)`. There is no move: `riassegna(…, null)` of a lone Segmento is `RiassegnazioneNonAmmessa`, and moving it would only renumber. |
| (c) P already has a Voce here | `RiassegnaSegmento(seg, P's target Voce)`. A manual move sets `confermato` (§3). |
| (d) otherwise | `RiassegnaSegmento(seg, null)`, which returns the new `VoceId`, then `ConfermaAttribuzione(new Voce, P / nuovo)` |

- **`RiassegnaSegmento` now returns `Esito<VoceId>`**, the destination, so step (d) knows the new Voce.
- **The steps are separate commands, not one transaction.** If (d)'s `ConfermaAttribuzione` fails
  (for example on `NomeGiaInUso`, [INV-16]), the new Voce stays **unnamed** with its one confirmed
  Segmento. Nothing is wrong and nothing is lost: the card shows the error, and the user names it
  there.
- The step that extracts a print, `ConfermaAttribuzione`, has ADR 0017's pending, waiting and
  "Annulla" behaviour. "Annulla" stops the steps not yet run, and steps already committed stay.

### 6. UI in S3 (R2, the panel block)
- **Entry point.**
  - With **exactly one** Segmento selected, the selection toolbar shows **"Dai un nome a questa
    frase ▾"**. The menu lists the `attivo` Parlanti, then "nuovo…".
  - A `confermato` Segmento shows a small **pin** marker. Its tooltip reads "Frase confermata:
    «Riassegna per somiglianza» non la sposta".
  - When it is selected, the toolbar offers **"Togli conferma"**.
- **Button "Riassegna per somiglianza"** in the Voci panel header *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1/(b).2: enabling rule, texts, preview state)*. It is **enabled iff** all of
  these hold:
  - **at least 2 `attivo` Parlanti have a frase di riferimento** in this Registrazione;
  - S3 is not read-only (ADR 0018 (b));
  - no Parlanti command is pending for this Registrazione;
  - no run is in progress.

  The disabled hint reads "Dai un nome ad almeno una frase di due persone diverse". Under the
  button: "Riferimenti: Anna, Marco", and, if any, "Senza frase di riferimento (non toccate):
  Luca". *(This refines the requested "enabled when ≥ 2 Voci are named": with explicit references
  only (§4.2), a named Voce without a confirmed sentence contributes nothing.)*
- **Running** (ADR 0017 §3, `SOGLIA_ATTESA_VISIBILE_MS = 2000`):
  - At once the button shows **"Confronto le frasi… n di N"** with a determinate bar. N is known,
    so a progress count is honest here, unlike diarization.
  - Every **editing** action of the panel and the toolbar is disabled, so no stale plan can be
    produced. Playback and "▶ estratto" still work.
  - If no progress tick arrives within `SOGLIA_ATTESA_VISIBILE_MS` → **"In attesa
    dell'elaborazione…"**.
  - **"Annulla"** is available for the whole computation. It restores the previous state, writes
    nothing, and shows no error.
  - The run is a **per-project** job:
    - leaving S3 does not cancel it, and coming back shows it running again;
    - closing the project cancels it and joins it;
    - S3 becoming read-only, because a Ritrascrivi of this Registrazione was queued, cancels it.
- **Result** (an inline message in the panel, dismissible, cleared by the next run or by leaving S3):
  - "**N frasi spostate, M incerte (rimaste dov'erano)**". Singular: "1 frase spostata", "1
    incerta".
  - "Nessuna frase da spostare (M incerte)".
  - On `TrascrittoCambiato`: "La trascrizione è cambiata durante il confronto: riprova".
  - Any other error follows AC-404's plain-language rule.
- Nothing runs on the UI thread (AC-417's rule).

### 7. Existing recordings
- **Ritrascrivi** (ADR 0018, being built) re-runs the **new** diarization. The replacement starts a
  fresh generation: every flag is 0, and the Attribuzioni of that Registrazione are purged.
  **Recommended order for Via Roquel:** Ritrascrivi first, then name the reference sentences, then
  "Riassegna per somiglianza". Names given before a Ritrascrivi are lost.
- **"Riassegna per somiglianza" does not need a re-run.** It works on any Trascritto, old
  diarization included, because it needs only Segmenti and references. On a recording the user has
  already named, it works as soon as each person gets a reference sentence.
- At the next start, S5 reports the new 40 MB TitaNet-S entry as missing and downloads it (ADR 0008
  flow, unchanged). The installed models all stay in use.

## Rejected options
- **Keep sherpa `FastClustering` and tune its threshold or `numClusters`.** Measured unstable (0.63,
  and 0.55–0.78 with other embeddings). The instability is the clustering, not the model.
- **TitaNet-S also in step 1, dropping ResNet34-LM.** Not measured; it is a later option (§1.1).
- **TitaNet-L / ERes2Net in the diarizer.** Same stability on Via Roquel, 25–40 % more time.
  TitaNet-L was stable on NR4 at k = 2 (0.958) but split it 727/136 s. That is a hint, not
  evidence; revisit it with the 3rd recording. They stay the
  print-model alternatives if the spike's cross-session calibration shows TitaNet-S is too weak.
  That switch would make every print stale, with no migration needed.
- **A single `RiassegnaPerSomiglianza` command in either context.** See §4.1: it would need names in
  Trascrizione, or a Parlanti → Trascrizione command edge.
- **N `RiassegnaSegmento` commands**, one per move. That means N transactions and N regenerations
  before coalescing, and a failure half-way leaves a half-applied plan. The multi-select failure
  flagged at the `schermata-registrazione-identificazione` pre-release (MED) is the same issue.
- **Implicit references** (every Segmento of an attributed Voce). They bring back the contamination
  this feature exists to remove, and they break idempotence. They are kept as a user option below. *(amended 2026-09-24 (b) [user]: see "Amendment 2026-09-24 (b)" (b).1: the user CHOSE them as a fallback when a person has no confirmed sentence; no longer rejected)*
- **An "incerto" bucket Voce.** See §4.4.
- **Storing references in Parlanti** (`Riferimento(segmentoId → parlanteId)`). It would need its own
  policy on every Revisione event and on `TrascrittoSostituito`. The flag on the Segmento moves with
  it and dies with its generation for free.
- **Caching per-Segmento embeddings** to make re-runs instant. ADR 0009 forbids any copy of a print
  beyond `impronta_vocale`. A re-run costs tens of seconds.
- **Batching several extractions per Mutex hold.** See §4.7.

## Consequences
- **Amended in place with dated pointers here:**
  - ADR 0014: runtime config rows, the catalogue embedding entry, "Embedding reuse", and the
    `k`-above-real behaviour;
  - ADR 0017 §1.1: the `diarizza` exception is narrowed to step 1;
  - ADR 0009: transient per-Segmento and per-piece embeddings.
- `architecture.md` gains one paragraph: the glue for a UI action that spans two contexts lives in
  `:avvio`.
- **Context map:** the spike line `impronta-vocale-affidabilita` is annotated "partially answered,
  model chosen (ADR 0019)", and its backlog file likewise. The spike stays open.
- **To be folded by their owners** (texts in the manifest delta):
  - the tactical model: [INV-8] reworded, [INV-26], [INV-27], the commands `ConfermaSegmento` and
    `RiassegnaSegmenti`, the event `SegmentoConfermato`, the read-model `PianoRiassegnazione`, and
    `RiassegnaSegmento` returning the destination;
  - the context-map ubiquitous language: **Segmento confermato**, **Frase di riferimento**,
    **Riassegna per somiglianza**, **frase incerta**;
  - `UI/ux-proposal.md` for S3.
- **Manifest:** `features/trascrizione-con-parlanti/manifest-deltas/2026-09-24-semi-automatica.md`
  (AC-480…). The manifest is authoritative once `build-manifest` folds it.
- **Release.** The diarization change is R1: it is inside `diarizzatore-sherpa`, and fixes the
  unstable R1 result. Everything else is R2 (Parlanti names), wired by `avvio-parlanti`. The
  Trascrizione-side additions (flag, migration, batch command, `SegmentoConfermato`) are
  release-neutral, as with ADR 0018.
- **Enforcement.**
  - `enforced_by` (above) has two clauses: no main source of `:avvio` or `:trascrizione:adattatori`
    references `model.int8.onnx` (the fp32 segmentation is used), and the TitaNet-S SHA is in
    `CatalogoDiarizzazione.kt`. It was validated on 2026-09-24 via `bash -c` and is **exit 1 on the
    tree by design** (`SelezioneAdattatoriMl.kt:82`). It becomes exigible when the
    `diarizzatore-sherpa` rework merges.
  - ADR 0004's `enforced_by` already confines `com.k2fsa` to `:ml-sherpa`.
  - ADR 0012 (b)'s prohibition already keeps extraction out of `..politiche`.
  - The testFixtures fakes already throw on an extraction inside a transaction.
  - **Discursive (code review):**
    - the clustering holds no Mutex;
    - `PianoRiassegnazione` keeps no embedding after returning;
    - the glue holds no domain rule;
    - `riassegnaInBlocco` never sets a flag nor creates a Voce;
    - no similarity number leaves `classificatore-somiglianza`.
- **Residual gaps [hypothesis until measured]:**
  - the reproduction of the experiment in Kotlin (§1.8, AC-488/489);
  - **NR4 at its real k = 2 is unstable (0.72)**: the new diarization is stable on one of the two
    recordings, not both;
  - the 10 % qualifying quota on short recordings;
  - `SIMILARITA_MINIMA`/`MARGINE_MINIMO`;
  - the idle-machine timings;
  - step 1's idle-machine hold (measured 76–80 s per 75 min under load).

  All have a named AC or a calibration task.

## Points for the user (the defaults above are in force unless the user chooses otherwise)
*(answered 2026-09-24 [user]: see "Amendment 2026-09-24 (b)" below. Point 1 → fallback; point 2 → preview; points 3 and 4 → the defaults confirmed; point 5 → no answer, the default stands.)*
1. **References explicit only** (default), or with an implicit fallback. With the fallback, a named
   Voce without a confirmed sentence would use all its Segmenti as references. That is more
   convenient, but it is contaminated and not idempotent.
2. **Apply at once with a summary and no undo** (default), or show a **preview** first ("Sposterò N
   frasi, M incerte · Applica / Annulla"), or offer an **"Annulla l'ultima riassegnazione"** that
   moves the Segmenti back. Emptied unnamed Voci would come back as new "Voce n" numbers.
3. **Incerte stay where they are** (default), or they are gathered into one new unnamed Voce "da
   verificare".
4. **Provisional Fasce go live.** When the real extractor lands, the Proposta shows Fasce with
   **provisional** `SoglieFascia`, because the spike is not calibrated. They could show a false
   "forte" on a different person. The Proposta never applies anything by itself, so the default is
   to show them. The alternative is to keep the Proposta off (`proposte = false`) until the spike
   closes.
5. **The new diarization is stable on Via Roquel, not on New Recording 4.** NR4 at its real
   k = 2 scored 0.72, versus 0.94 on Via Roquel at k = 4. The default is to ship it anyway: it
   beats the current 0.63, and §4 corrects the result. The alternative is to wait for a 3rd
   recording and compare with TitaNet-L first.

## Amendment 2026-09-24 (b) — the user's answers to the Points for the user [user]
Source: the user's answers of 2026-09-24 (`dispatch.log`, `(adr-0019) decision`). The text above is kept.
Where it disagrees, this amendment wins. Manifest:
`features/trascrizione-con-parlanti/manifest-deltas/2026-09-24-semi-automatica.md`, updated in the same pass.
New ACs are **AC-543…AC-550**. The ACs reworded are listed in the delta.

### (b).1 References: confirmed sentences first, then the whole named Voce [user] (Point 1)
**Reference Parlanti.** For a Registrazione R, a **reference Parlante** is an `attivo` Parlante P who
is attributed to ≥ 1 Voce of R and has ≥ 1 reference, in one of two modes:
- **"frasi confermate".** P has at least one `confermato` Segmento of ≥ 1 000 ms on its Voci. Then
  P's references are those Segmenti only. This is §3 and §4.2, unchanged.
- **"intera Voce" (the fallback).** P has no such Segmento. Then P's references are **every Segmento
  of ≥ 1 000 ms on every Voce attributed to P** in R. Segmenti shorter than 1 s are still excluded.
  - *Interpretation [architect, flagged]:* "no confirmed sentence" is read as "no confirmed sentence
    of ≥ 1 s". A sub-second confirmed Segmento can never be a reference, so it does not block the
    fallback.

**Unchanged.**
- An `eliminato` Parlante never has references, even when a confirmed Segmento sits on its Voce
  (ADR 0009). An `occasionale` may have references.
- A plan needs **≥ 2 reference Parlanti**. Otherwise it returns `Errore(RiferimentiInsufficienti)`
  with no extraction.
- P's centroid is computed as in §4.3, over the references of P's mode.

**Frozen Voci, precisely.** A Voce is **frozen** (never extracted, never moved from, never a target)
iff it is **attributed** and its Parlante is **not** a reference Parlante:
- the Parlante is `eliminato`; or
- the Parlante is `attivo` but has **no Segmento of ≥ 1 000 ms** on any of its Voci in R, so there is
  nothing to compare with.

**A named Voce with no confirmed sentence is NOT frozen any more.** Its Segmenti are P's references,
and its non-`confermato` Segmenti ≥ 1 s are also **movable**. Unattributed Voci are never frozen.
Movable Segmenti are unchanged otherwise: not `confermato`, ≥ 1 000 ms (shorter ones are
`incerte`), on a Voce that is not frozen.

**One extraction per Segmento.** In the "intera Voce" mode a Segmento is both a reference and a
candidate. It is extracted **once**, and that embedding serves both roles. The extracted set is
references ∪ movable Segmenti ≥ 1 s, still about 1 000 per hour (§4.7).
- Its own embedding is part of P's centroid. That biases it towards staying, which is the
  conservative direction.
- A leave-one-out centroid was not chosen for v1: it is more cost for an unmeasured gain. It is the
  first alternative for the calibration of §4.3.

**Consequences.**
- **Idempotence is no longer guaranteed with fallback references.** §4.6 now holds only when
  **every** reference Parlante is in the "frasi confermate" mode.
  - With ≥ 1 "intera Voce" Parlante, each run is computed on the **current** state. Segmenti moved
    onto P's target Voce join P's references at the next run, and Segmenti moved away leave them.
  - So the centroids move, and a second run may move **more** Segmenti. In principle it may also
    move a Segmento back.
  - There is no convergence loop and no automatic re-run. Every run goes through the preview
    ((b).2), so the user sees the count before anything is written, and can `Annulla`.
  - The stable path is shown in the UI: confirm one sentence per person, and the run becomes
    idempotent again.
- **Contamination is accepted.** An "intera Voce" centroid includes whatever the diarizer mixed into
  that Voce. The user chose this for convenience, and the preview plus the confirmed-sentence path
  mitigate it.
- **A reference Parlante's Voci may now empty.** The §4.4 sentence "the automatic pass never removes
  an `Attribuzione`" is false under the fallback. It is replaced by a **guard**, part of INV-27:
  - A plan never leaves a reference Parlante with **no Segmento in R**.
  - If applying it would, every spostamento whose `da` is a Voce of that Parlante is dropped, and
    those Segmenti count as `incerte`.
  - The check repeats until nothing changes. Each round only drops moves, so there is at most one
    round per reference Parlante, and the result is deterministic.
  - So the automatic pass never removes a reference Parlante's **last** `Attribuzione` in R, and
    never triggers [INV-25].
  - It **may** empty a non-target Voce of P and so remove that Voce's `Attribuzione` through the
    existing revisione-policy. This merges P's Voci into its target, and P keeps a Voce.
- **Batch semantics (found at fold, rule 19).** A plan's spostamenti are in (inizio, segmentoId)
  order. A move out of a target Voce may therefore precede moves into it. Removing a Voce as soon as
  it empties would make a later entry's `a` "missing", which is a spurious `TrascrittoCambiato`.
  §4.5 is therefore made precise:
  - `riassegnaInBlocco` **validates every entry against the pre-batch state**.
  - It applies all the moves.
  - It then removes the Voci that are **empty at the end of the batch** ([INV-6]). A Voce emptied
    and refilled within the same batch is kept.
  - `daRimossa = true` on the **last** move out of each Voce that is empty at the end.
  - The refusal cases and the all-or-nothing rule are unchanged.
- **Cost and privacy are unchanged** (§4.7, §4.8).

**[INV-27] REWORDED** (Parlanti, `piano-riassegnazione` read-model). It replaces the §4.4 text:
> [INV-27] A `PianoRiassegnazione` of Registrazione R moves a `Segmento` s to Voce T only if all of
> these hold:
> - s is not `confermato`, is ≥ 1 000 ms, and lies on a Voce that is not frozen (unattributed, or
>   attributed to a reference `Parlante`);
> - s is classified Sicura(P) for a reference `Parlante` P;
> - T is P's target Voce (the lowest `voceId` attributed to P in R), and s is not already on T;
> - after the whole plan, every reference `Parlante` still has ≥ 1 `Segmento` in R. Otherwise every
>   move out of that `Parlante`'s Voci is dropped and counted as incerta (repeated until stable).
>
> A **reference `Parlante`** is an `attivo` `Parlante` attributed to ≥ 1 Voce of R that has ≥ 1
> reference: its `confermato` `Segmento`s of ≥ 1 000 ms if it has any ("frasi confermate"), otherwise
> every `Segmento` of ≥ 1 000 ms on its Voci ("intera Voce"). An `eliminato` never is one. A plan
> needs ≥ 2 of them. It writes nothing, keeps no embedding after it returns, and exposes no
> similarity number.

### (b).2 A preview before anything is written [user] (Point 2)
"Riassegna per somiglianza" becomes **compute → preview → apply**. Nothing is written until
**Applica**.
1. **Calcola.** This is the running phase of §6, unchanged: `PianoRiassegnazioneQuery.calcola`,
   determinate progress, "In attesa dell'elaborazione…", and "Annulla". It ends in an **Anteprima**
   and writes nothing.
2. **Anteprima.** The panel shows:
   - "**Sposterò N frasi, M incerte restano dove sono**";
   - then one line per (da → a) pair, "Voce 3 → Anna: 8", using each Voce's current name or
     "Voce n". Lines are grouped by destination person and ordered by destination, then source
     (`voceId`);
   - the buttons **Applica** and **Annulla**.

   With N = 0 it shows "Nessuna frase da spostare (M incerte restano dove sono)" with **Chiudi** only.
   Singular forms: "1 frase", "1 incerta resta dove è".
3. **Applica** executes the **same** plan. The held `PianoRiassegnazione`'s spostamenti are sent 1:1
   as `RiassegnaSegmenti`. **Nothing is recomputed and nothing is extracted**, so Applica cannot
   produce a different plan. The final transaction cannot be cancelled. The result is §6's "N frasi
   spostate, M incerte (rimaste dov'erano)".
4. **The transcript changed in between.** Applica returns `Errore(TrascrittoCambiato)`. Nothing is
   written and the plan is discarded. The message reads "La trascrizione è cambiata dopo il
   confronto: ricalcola l'anteprima", with a **Ricalcola** button that runs step 1 again. This text
   replaces §6's "…durante il confronto: riprova" and AC-515's `MessaggiErrore` text.
5. **Annulla** on the preview discards the plan. Nothing is written and no message is shown.

**The held plan.**
- The `:avvio` glue keeps **at most one plan per Registrazione**, in the per-project scope, in
  memory only, and never persists it.
  - The plan holds ids, `VoceId`s and intervals only: **no embedding, no number**. So holding it
    until Applica is compatible with ADR 0009 and §4.8.
- The plan is dropped:
  - on Applica, whatever the outcome;
  - on Annulla;
  - on project close;
  - when a Ritrascrivi of that Registrazione is queued (S3 turns read-only, ADR 0018).
- Leaving S3 keeps the plan. Coming back shows the preview again.

**While the preview is open,** every editing action of the panel and the toolbar stays disabled, as
while computing. Playback and "▶ estratto" work. S3 itself therefore cannot make the plan stale.
`TrascrittoCambiato` stays the guard for everything else.

**Still no undo command** (§4.6). The preview is the safety the user chose.

**The UI port changes (manifest B8).** `AzioniSomiglianza` has:
- `calcola(id)`, which replaces `avvia`;
- `applica(id)`;
- `annulla(id)`, which interrupts a computation or discards a preview;
- `stato`.

`StatoSomiglianza` gains `Anteprima(gruppi, incerte)` and `Applicazione`. The glue groups the
spostamenti into `GruppoSpostamenti(da, a, frasi)`, which is pure presentation mapping, not a domain
rule.

### (b).3 Incerte stay where they are [user] (Point 3) — the default is confirmed
There is no "da verificare" bucket. §4.4 is unchanged.

### (b).4 Provisional Proposte are shown [user] (Point 4) — the default is confirmed
When `estrattore-impronta-sherpa` lands, R2's REALI composition sets `proposte = true` with the
provisional `SoglieFascia` (AC-541).

### (b).5 The experiment's settings (former Point 5)
They are copied into **§1.9 "Parametri misurati"**. The `diarizzatore-sherpa` worker reproduces them
exactly: AC-484 and AC-485 are reworded for linkage by piece count and for the stability method.

**Point 5 (NR4 unstable at k = 2)** got no answer. Its default stands: ship the new diarization.

### (b).6 S3 texts (replacing the §6 wording where they differ)
- **The button is enabled iff all of these hold:**
  - ≥ 2 `attivo` Parlanti attributed in R each have ≥ 1 Segmento of ≥ 1 000 ms on their Voci, so
    each has a reference in either mode;
  - S3 is not read-only;
  - no Parlanti command or `nominaFrase` is pending;
  - no computation, preview or application is in progress.
- **Disabled hint:** "Dai un nome ad almeno due persone".
- **Under the button:**
  - "Riferimenti: Anna, Marco (frasi confermate) · Luca (tutta la voce)";
  - if any person is in the "intera Voce" mode: "Senza una frase confermata uso tutta la voce: il
    risultato può cambiare se ripeti. Conferma una frase per persona per renderlo stabile.";
  - if any attributed `attivo` person is frozen: "Non toccate: <Nomi>".
