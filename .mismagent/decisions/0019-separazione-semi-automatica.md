---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0014 (runtime config rows Segmentation file / Embedding / Clustering, the embedding catalogue entry, "Embedding reuse"); ADR 0017 §1.1 (diarizza no longer a single native call); ADR 0009 (transient per-Segmento embeddings); tactical INV-8 wording
closes_spike: null  # impronta-vocale-affidabilita is PARTIALLY answered (model chosen, §2); it stays open for SoglieFascia / BUDGET_IMPRONTA_MS / go-no-go
enforced_by:
  kind: presence+prohibition
  rule: "! grep -rnE --include='*.kt' --exclude-dir=build 'embedding-wespeaker-resnet34-lm|wespeaker_en_voxceleb_resnet34_LM' modelli/src/main avvio/src/main trascrizione/adattatori/src/main parlanti/adattatori/src/main && grep -q 'ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e' modelli/src/main/kotlin/snastro/modelli/CatalogoDiarizzazione.kt"
  exigible_from: "diarizzatore-sherpa"   # its 2026-09-24 (ADR 0019) rework; red on the tree today BY DESIGN (validated 2026-09-24: exit 1, the old entry is still in CatalogoDiarizzazione.kt)
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
| Segmentation | pyannote segmentation-3.0, **fp32** | `segmentazione-pyannote-3.0` (**unchanged**: same asset, same SHA) | **`model.onnx`** (5,992,913 B). Before this ADR it was `model.int8.onnx`. |
| Embedding (diarization **and** `ImprontaVocale`, §2) | NVIDIA NeMo **TitaNet-small** | **`embedding-nemo-titanet-small`** (new id) | `nemo_en_titanet_small.onnx` |

`embedding-wespeaker-resnet34-lm` **leaves the catalogue**: no role uses it any more. The folder
already downloaded stays on disk, unused and harmless (see Consequences).

#### 1.2 Pipeline of `Diarizzatore.diarizza(c, numeroPersone)`
The port stays exactly as it is (`tec-diarizzatore`: `List<Turno>`, at most k distinct `voceIndice`,
never persisted). Only the adapter changes.
1. **Step 1: speech regions and local speaker changes (native, sherpa).** Run
   `OfflineSpeakerDiarization` with seg-3.0 fp32 and TitaNet-small, `windowShiftRatio = 0.5`,
   `minDurationOn = 0.3`, `minDurationOff = 0.5`.
   - Its **cluster labels are discarded**. We keep only its segments, which carry the speech
     regions, the overlaps, and the local speaker-change boundaries found by segmentation.
   - Step 1 clusters with an **over-splitting** setting, so that a speaker change inside continuous
     speech still ends a segment. The exact value (`SOGLIA_PASSO_1`) is **the one the 2026-09-24
     experiment used**. It is not in the research file, and the block must reproduce the experiment
     and record it (§1.8).
2. **Step 2: pieces.** Cut each step-1 segment into ⌈d / `PEZZO_MS`⌉ equal pieces, with
   `PEZZO_MS = 3000`, so every piece is ≤ 3 s. A piece never crosses a step-1 boundary. When
   step-1 segments overlap, both keep their pieces ([INV-7]: nothing is trimmed).
3. **Step 3: one embedding per piece** with TitaNet-small, through the `:ml-sherpa` embedding
   wrapper (§1.4).
4. **Step 4: our own agglomerative clustering**, pure Kotlin.
   - Linkage is average linkage on cosine distance, over the pieces ≥ `DURATA_MINIMA_PEZZO_AHC_MS`
     (1000 ms).
   - A cluster **qualifies** when its speech is ≥ `min(DURATA_MINIMA_CLUSTER_MS = 60 000,
     QUOTA_MINIMA_CLUSTER = 10 % × total speech)`. The 60 s is measured. The 10 % cap is a design
     choice, so a short recording is not left with no qualifying cluster; it is unmeasured on
     recordings shorter than 19 min.
   - **With `numeroPersone = k`:** cut the dendrogram at m = k, k+1, … clusters, and stop at the
     first m where at least k clusters qualify. Keep the k with the most speech.
     - If no m ever reaches k qualifying clusters, the audio holds fewer voices. Keep the qualifying
       clusters at the first m where their count is highest, so the result has **< k** voices. That
       still satisfies the port's "at most k".
     - If nothing qualifies, keep one cluster with all the speech.
   - **Without it:** cut at the cosine distance `SOGLIA_AHC_AUTO`, then keep the qualifying
     clusters, at least one. **The value is not measured.** The block calibrates it on the two
     recordings (§1.8) and records it by amendment. Auto clustering is expected to over-count, and
     the §4 flow repairs that.
   - Every tie breaks on the lowest index. Input order is time order, so the output is deterministic.
5. **Step 5: nearest-centroid assignment.** A centroid is the normalized mean of a kept cluster's
   normalized embeddings. **Every** piece goes to its nearest centroid, including short pieces and
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
- **`:avvio`** (`SelezioneAdattatoriMl`, R1 wiring) passes the fp32 file and the TitaNet file. The
  catalogue list drops ResNet34-LM.

#### 1.5 The native Mutex (amends ADR 0017 §1.1: `diarizza` is no longer "a single native call by nature")
- Step 1 is **one** hold. It is a single native call and cannot be interrupted, as before.
- Step 3 takes **one hold per piece**, with the model cached. This is the §1.1 rule that ASR
  already follows.
- Steps 2, 4 and 5 run with the Mutex **free**.

The longest wait an extraction can meet during diarization therefore shrinks from "the whole
diarization" to "step 1". Step 1's share of the ~100 s is not measured, so this is **[hypothesis]**
until `benchmark-elaborazione` prints the split. ADR 0017's visible, cancellable wait on S3 is
unchanged.

#### 1.6 Time budget (ADR 0011: 60 min in ≤ 600 s on the M3 Pro)
- **Diarization** measured about 100 s per 75 min, which is **≈ 80 s per hour**. The old setup took
  245 s per 75 min with auto clustering, both under load.
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
(ADR 0008 (c)).

#### 1.8 What the build must reproduce (the experiment script is not in the repo)
Two opt-in `@modelli` ACs gate the acceptance:
- **Stability.** A real sample of ≥ 15 min, at the real k, is run as is and shifted by +10 ms and
  +500 ms. The best one-to-one mapping, weighted by speech time, must give agreement **≥ 0.90**.
- **Reproduction.** Via Roquel at k = 4 gives 4 voices whose speech is within ±15 % of 1136, 971, 803
  and 584 s.

The block then records `SOGLIA_PASSO_1` and `SOGLIA_AHC_AUTO` (the latter from a sweep on Via
Roquel, which has 4 speakers, and NR4, which has 2) as an amendment to this ADR. If the stability
figure cannot be reproduced, the block stops and returns to the architect. It must never ship a
"close enough" clustering silently.

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
  Parlanti: it is derived live from the Voci and the Attribuzioni.
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
   in the **per-project scope** of ADR 0017 §3, and holds no domain logic.

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
- **References are explicit only.** Segmenti that merely sit in an attributed Voce are **not**
  references. The initial diarization mixed people into those Voci, so using them would bring the
  contamination back. The alternative (implicit fallback) is under "Points for the user".
- **Target Voce of P:** the **lowest `voceId`** among the Voci attributed to P in this Registrazione.
  This rule is simple and deterministic, and the S3 naming action (§5) uses the same one.
- **Movable Segmenti:** every Segmento that meets both conditions:
  - it is **not** `confermato`;
  - its Voce is **unattributed**, or attributed to a **reference** Parlante.

  Segmenti on a Voce attributed to a Parlante **without references** are **frozen**: never
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
The automatic pass therefore never removes an `Attribuzione`.

**New invariant [INV-27]** (Parlanti, `piano-riassegnazione` read-model):
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
  applies the moves in list order, **without** creating a Voce or touching a flag ([INV-26]). The
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
- **Idempotent** with unchanged inputs:
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
  "Points for the user".

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
- **Button "Riassegna per somiglianza"** in the Voci panel header. It is **enabled iff** all of
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
- The old `embedding-wespeaker-resnet34-lm` directory stays in the model cache, unused. At the next
  start S5 reports the new 40 MB entry as missing and downloads it (ADR 0008 flow, unchanged).

## Rejected options
- **Keep sherpa `FastClustering` and tune its threshold or `numClusters`.** Measured unstable (0.63,
  and 0.55–0.78 with other embeddings). The instability is the clustering, not the model.
- **TitaNet-L / ERes2Net in the diarizer.** Same stability, 25–40 % more time. They stay the
  print-model alternatives if the spike's cross-session calibration shows TitaNet-S is too weak.
  That switch would make every print stale, with no migration needed.
- **A single `RiassegnaPerSomiglianza` command in either context.** See §4.1: it would need names in
  Trascrizione, or a Parlanti → Trascrizione command edge.
- **N `RiassegnaSegmento` commands**, one per move. That means N transactions and N regenerations
  before coalescing, and a failure half-way leaves a half-applied plan. The multi-select failure
  flagged at the `schermata-registrazione-identificazione` pre-release (MED) is the same issue.
- **Implicit references** (every Segmento of an attributed Voce). They bring back the contamination
  this feature exists to remove, and they break idempotence. They are kept as a user option below.
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
  - `enforced_by` (above): no main source references the ResNet34-LM id or file, and the TitaNet-S
    SHA is in `CatalogoDiarizzazione.kt`. It was validated on 2026-09-24 and is **exit 1 on the tree
    by design**, because the old entry is still present. It becomes exigible when the
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
  - the step-1 setting and `SOGLIA_AHC_AUTO`, which the block must reproduce and record (§1.8);
  - the 10 % qualifying quota on short recordings;
  - `SIMILARITA_MINIMA`/`MARGINE_MINIMO`;
  - the idle-machine timings;
  - step 1's share of the Mutex hold.

  All have a named AC or a calibration task.

## Points for the user (the defaults above are in force unless the user chooses otherwise)
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
5. **The experiment's step-1 settings.** Settings = the step-1 clustering value, piece handling, and
   how "stability" was scored. The orchestrator (or the session that ran it) should hand them to
   the `diarizzatore-sherpa` worker. Otherwise the worker reconstructs them against AC-488/489.
