# Manifest delta — semi-automatic voice separation (ADR 0019, user decision 2026-09-24)
This is the authoritative input for /mismagent:build-manifest. Fold it into building-blocks.yaml and
regenerate the rich block files.

Source: ADR 0019 (decisions/0019-separazione-semi-automatica.md). It amends in place, with dated
pointers, ADR 0014 (diarization config + catalogue), ADR 0017 §1.1 (diarizza holds), ADR 0009
(transient embeddings) and architecture.md (cross-context UI glue in `:avvio`).

New ACs start at **AC-480**. The current max is AC-479, plus AC-155bis and AC-186bis. This delta uses
**AC-480…AC-542**.

**Release.**
- **R1:** the diarization change inside `diarizzatore-sherpa`, which fixes the unstable R1 result.
- **R2:** everything that names people, wired by `avvio-parlanti`.
- **Release-neutral, built in the R2 wave:** the Trascrizione additions (the `confermato` flag,
  `4.sqm`, `RiassegnaSegmenti`, `ConfermaSegmento`, `SegmentoConfermato`). They are harmless in R1,
  where no composition triggers them.

**Open user points (ADR 0019 "Points for the user").** The ACs below encode the DEFAULTS:
- explicit references only;
- apply at once with no preview and no undo;
- incerte stay where they are;
- the Proposta goes live with provisional Fasce.

If the user picks another option, the ACs noted `[default §P-n]` change before the fold.

## Summary of the decision (see the ADR for the rationale)
- **Diarization** (`diarizzatore-sherpa`; the `Diarizzatore` port is unchanged):
  - step 1, sherpa `OfflineSpeakerDiarization` with seg-3.0 **fp32** + **TitaNet-small**. Only its
    segments are kept, not its labels;
  - ≤ 3 s pieces;
  - one TitaNet-S embedding per piece;
  - our own average-linkage cosine AHC, which cuts at k while ignoring clusters < min(60 s, 10 % of
    speech), or cuts at `SOGLIA_AHC_AUTO` when there is no k;
  - nearest-centroid assignment of every piece.

  The pure clustering lives in `:trascrizione:adattatori ..ml`. The new `EmbeddingSherpa` wrapper
  lives in `:ml-sherpa`.
- **Catalogue:**
  - new `embedding-nemo-titanet-small`;
  - `embedding-wespeaker-resnet34-lm` removed;
  - the segmentation entry is unchanged, but its file is now `model.onnx`.
- **TitaNet-small is also the ImprontaVocale model** → `estrattore-impronta-sherpa` is unblocked. The
  `impronta-vocale-affidabilita` spike is partially answered, and `SoglieFascia` stays provisional.
- **Reference sentences.** `Segmento.confermato` (Trascrizione, migration `4.sqm`, [INV-26]) is set
  by a manual riassegna, by dividi's S, and by `ConfermaSegmento`. A confirmed Segmento of ≥ 1 s on
  a Voce attributed to an attivo P is a **frase di riferimento** of P.
- **"Riassegna per somiglianza"** has three parts:
  - the Parlanti read-model `PianoRiassegnazione` ([INV-27]; no writes, no numbers);
  - the Trascrizione batch command `RiassegnaSegmenti`: ONE transaction, all or nothing, N
    `SegmentoRiassegnato`, a stale plan → `TrascrittoCambiato`;
  - the glue in `avvio-parlanti`.

## BOUNDARIES
- **B1 `agg-trascritto`** (owner trascritto) — add to `pinned_types`:
  - `Segmento.confermato`: `Boolean`, read-only, [INV-26].
  - `Trascritto.riassegnaInBlocco`: `(spostamenti: List<SpostamentoSegmento>): Esito<List<SegmentoRiassegnato>>`.
    It moves in list order, never creates a Voce, never changes a flag. Any stale entry
    (Segmento missing / not on `da` / interval differs / `a` missing / Segmento confermato) →
    `Errore(TrascrittoCambiato(registrazioneId))`. `a == da` or a duplicated segmentoId →
    `Errore(RiassegnazioneNonAmmessa)`. A refusal leaves the state unchanged. An empty list →
    `Ok(emptyList())`.
  - `SpostamentoSegmento`: `data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs)`, a `:trascrizione:dominio` input VO.
  - `Trascritto.confermaSegmento`: `(segmento: SegmentoId, confermato: Boolean): Esito<SegmentoConfermato?>`.
    The same value → `Ok(null)`, with no change. An unknown segment → `Errore(SegmentoNonTrovato)`.
  - `Trascritto.riassegna`: returns the event carrying `a`, as before, and now also sets
    `confermato = true` on the moved Segmento. `Trascritto.dividi` sets `confermato = true` on S.
    `Trascritto.unisci` keeps the flags.
  - `ErroreTrascrizione.TrascrittoCambiato(registrazioneId: RegistrazioneId)`: new.
- **B2 `eventi-revisione`** (owner eventi-pubblicati):
  - Add `SegmentoConfermato`: `data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, confermato: Boolean) : EventoPubblicato`.
  - Delivery: after commit only (view refresh). There is no synchronous subscriber.
  - Add `supplier` `riassegna-segmenti` (which emits `SegmentoRiassegnato` too), and consumers
    `riassegna-segmenti` and `avvio-parlanti`.
  - Delivery note: a `RiassegnaSegmenti` commit publishes N `SegmentoRiassegnato` in list order.
    The synchronous revisione-policy runs once per event, inside the transaction. After-commit
    subscribers are coalesced per `registrazioneId`, as today.
- **B3 `voci-per-parlanti`** (owner porta-lettore-voci; consumer-driven, Parlanti is the consumer):
  - Add `LettoreVoci.segmenti`:
    `fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce>?`. It returns `null` iff there is no
    Trascritto. It returns every current Segmento, ordered by (`inizioMs`, `segmentoId`), and
    **never text**.
  - Add `SegmentoDiVoce`: `data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, confermato: Boolean)`.
  - Add the consumer `piano-riassegnazione`.
- **B4 NEW `tec-classificatore-somiglianza`** (owner porte-parlanti; consumers piano-riassegnazione,
  classificatore-somiglianza; in-process, consumer-driven):
  - `ClassificatoreSomiglianza`:
    `interface { fun classifica(riferimenti: Map<ParlanteId, List<Impronta>>, frasi: List<Impronta>): List<Classificazione> }`.
    - The output has the same size and order as `frasi`.
    - `riferimenti` has ≥ 2 keys, each non-empty. The caller guarantees it (`require`).
    - It never throws on print data.
  - `Classificazione`: `sealed interface { data class Sicura(val parlanteId: ParlanteId); data object Incerta }` (in `:parlanti:applicazione`). No number.
  - `SoglieSomiglianza`: `data class(minima: Double, margine: Double)`. `require(margine > 0 && minima in -1.0..1.0)`. Injected as config. PROVISIONAL, ADR 0019 §4.3.
  - `ClassificatoreSomiglianzaFinta`: in testFixtures, with a configurable table from frase index to
    `Classificazione`.
- **B5 NEW `piano-per-somiglianza`** (owner piano-riassegnazione; consumer avvio-parlanti; in-process,
  consumer-driven: the glue is the consumer):
  - `PianoRiassegnazioneQuery`:
    `fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione>`.
    - Errors: `TrascrittoNonTrovato`, and the new `ErroreParlanti.RiferimentiInsufficienti(registrazioneId)`.
    - It throws `InterruptedException` on cancellation. It never opens a transaction.
  - `PianoRiassegnazione`: `data class(registrazioneId, spostamenti: List<SpostamentoProposto>, incerte: Int)`.
  - `SpostamentoProposto`: `data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs)`.
    It is primitives + kernel VOs only; the glue maps it 1:1 to `SpostamentoSegmento`.
- **B6 `tec-ml-sherpa`** (owner ml-sherpa-motore; add consumer diarizzatore-sherpa for the new type):
  - Add `snastro.ml.EmbeddingSherpa`:
    `class(motore: MotoreSherpa, percorsoModello: Path, threadIntraOp: Int) : AutoCloseable { fun calcola(campioni: FloatArray): FloatArray; override fun close() }`.
    - ONE `conSessione` per `calcola`.
    - The model is loaded at the first call, cached across calls, and released by `close` (the
      `RiconoscitoreSherpa` pattern).
    - Mutex rules per ADR 0017 §1.
  - Owner of the code: `diarizzatore-sherpa` (R1, first user), as the module note says.
- **B7 `tec-diarizzatore`**: no signature change. Replace the note "numeroPersone = k → … (ADR
  0014)" with "(ADR 0014 rules; clustering per ADR 0019 §1.2: at most k; with k above the real count
  it tends to split one voice, never to fail)".
- **B8 UI port (owner schermata-registrazione-identificazione, implemented by avvio-parlanti)** in
  `snastro.ui.registrazione`:
  - `AzioniSomiglianza`, which has:
    - `fun avvia(id: RegistrazioneId)`;
    - `fun annulla(id: RegistrazioneId)`;
    - `val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>>`.
  - `StatoSomiglianza`: `sealed { InCorso(fatti: Int, totale: Int, ultimoAvanzamentoMs: Long); Esito(spostate: Int, incerte: Int); Errore(messaggio: ErroreSomiglianzaUi) }`.
  - Add `ComandiVoce.nominaFrase(registrazioneId, segmentoId, passi: PassiNominaFrase)` in the same
    per-project scope and pending state as ADR 0017 §3, keyed by the Segmento. The UI-side
    `PassiNominaFrase` (sealed: `SoloConferma | AttribuisciVoce(voceId, obiettivo) | Sposta(voceId) | NuovaVoce(obiettivo)`)
    is computed by the presenter.

## NEW BLOCKS
### `persistenza-conferma-segmento` (adapter, piattaforma, R2, wave 3, `:persistenza`; depends_on persistenza-ritrascrivi; related_adrs 0006, 0019)
- AC-521 `migrations/4.sqm` (schema 4 → 5, forward-only) holds exactly one statement:
  `ALTER TABLE segmento ADD COLUMN confermato INTEGER NOT NULL DEFAULT 0 CHECK (confermato IN (0, 1));`.
  `SnastroDatabase.Schema.version = 5`.
  - The CR-13 migration test stays green: `Schema.migrate` from empty equals `Schema.create`.
  - Fixture test: a DB frozen at version 4, with a Trascritto of 3 Segmenti, migrates to 5 with every
    row intact and `confermato = 0`.
  - Inserting `confermato = 2` is refused.
- Notes: `Segmento.sq` queries read and write the column. The fold must name the order: this block is
  built before the `repository-sql-trascrizione` rework, whatever the release.

### `riassegna-segmenti` (application-service, trascrizione, R2, wave 4, `:trascrizione:applicazione ..comandi`; consumes kernel-pl, agg-trascritto, repo-trascrizione, eventi-revisione; related_adrs 0003, 0012, 0019)
- Command `RiassegnaSegmenti(registrazioneId: RegistrazioneId, spostamenti: List<SpostamentoSegmento>)`.
- AC-518 One `inTransazione`: `trova` → `riassegnaInBlocco` → ONE `salva` → the N
  `SegmentoRiassegnato` published in list order. Each has `aNuova = false`, and `daRimossa = true`
  exactly on the move that empties its `da`. Test with a recording synchronous subscriber: N
  deliveries in order, all inside the one transaction.
- AC-519 If the synchronous subscriber returns `Esito.Errore` on the k-th event, the whole batch is
  rolled back. The Trascritto equals its pre-command state, and after-commit subscribers see nothing
  (AC-83 rule).
- AC-520 A stale plan (each case of B1 in a table) → `Errore(TrascrittoCambiato)`. Nothing is
  written and no event is published. No Trascritto → `TrascrittoNonTrovato`. An empty list →
  `Ok(Unit)` with no transaction write and no event.

### `piano-riassegnazione` (read-model, parlanti, R2, wave 5, `:parlanti:applicazione ..letture`; consumes kernel-pl, agg-parlante, agg-attribuzione, repo-parlanti, voci-per-parlanti, tec-decodifica-parlanti, tec-estrattore-impronta, tec-classificatore-somiglianza, sorgente-impronta-pl; related_adrs 0009, 0012, 0017, 0019)
- invariants: INV-27 (text in ADR 0019 §4.4).
- view_shape: `piano: {registrazioneId, spostamenti: List<{segmentoId, da, a, intervallo}>, incerte: Int}`.
  view_sources:
  - spostamenti ← `LettoreVoci.segmenti`, `AttribuzioneRepository.diRegistrazione`, `Parlante`
    (stato `attivo`), and `ClassificatoreSomiglianza` over transient `Impronta`s;
  - incerte ← the movable Segmenti that are Incerta or < 1 000 ms.
- INV-27 table test: a Segmento moves only if all four conditions of INV-27 hold. One row per
  failing condition, each giving no spostamento.
- AC-501 References: `confermato` Segmenti of ≥ 1 000 ms on Voci attributed to an `attivo` Parlante.
  - A Parlante `eliminato` has none, even if a confirmed Segmento sits on its Voce.
  - A Parlante whose only confirmed Segmenti are < 1 000 ms has none.
  - A confirmed Segmento on an unattributed Voce is no one's reference.
- AC-502 Fewer than 2 reference Parlanti → `Errore(RiferimentiInsufficienti)`, with 0 calls to
  `DecodificatoreAudio` and `EstrattoreImpronta` (counting fakes).
- AC-503 Frozen Voci: the Segmenti of a Voce attributed to a Parlante without references (or an
  `eliminato`) are never extracted, never appear in `spostamenti` as `da`, and are never a target
  `a`. Confirmed Segmenti never appear in `spostamenti`.
- AC-504 Movable Segmenti < 1 000 ms are not extracted. Each counts once in `incerte`.
- AC-505 Target Voce = the lowest `voceId` among the Voci attributed to the chosen Parlante (fixture:
  P on Voci 4 and 2 → target 2). A Sicura Segmento already on the target → no spostamento. One on
  another Voce, including P's other Voce 4 → `spostamento(da, a = 2, intervallo = the Segmento's)`.
- AC-506 Incerta → no spostamento, counted in `incerte`.
- AC-507 Exactly one `estrai` per extracted Segmento (references + movable ≥ 1 s), each over
  `DecodificatoreAudio.campioni(id, SorgenteImpronta.di(listOf(intervallo)).intervalli)`.
  - No transaction is open: the fakes throw if one is.
  - `progresso(fatti, totale)` is called once after each `estrai`, with a constant `totale` equal to
    the number of extractions and `fatti` = 1..totale.
- AC-508 An `InterruptedException` from `estrai` (at the 3rd call) propagates. There is no result,
  nothing is written, and the next `calcola` extracts again from the start.
- AC-509 `calcola` writes nothing: attribuzione / impronta_vocale / parlante row counts are unchanged.
  Two consecutive `calcola` calls make 2× the `estrai` calls, so no embedding is retained (ADR 0009;
  code review: no field or cache holds an `Impronta`).
- AC-510 Idempotence: apply the plan to the `LettoreVoci` fake (move the Segmenti), keep the same
  deterministic extractor fake, and call `calcola` again → `spostamenti` is empty and `incerte` is
  unchanged.
- Notes: the plan's `intervallo` is the Segmento's current interval, the stale-guard of B1. The
  `PianoRiassegnazione` exposes no similarity (by type).

### `classificatore-somiglianza` (adapter, parlanti, R2, wave 4, `:parlanti:adattatori ..ml`; consumes kernel-pl, tec-classificatore-somiglianza; related_adrs 0004, 0019)
- AC-496 `ClassificatoreSomiglianzaContratto` passes against the real implementation, in the gate,
  with no models.
- AC-497 With `SoglieSomiglianza(0.30, 0.05)` and orthogonal unit references A and B, table test:
  - frase = A → Sicura(A);
  - frase at 45° (cos 0.707 / 0.707) → Incerta (margin 0);
  - cos(A) = 0.25, cos(B) = 0 → Incerta (below minima);
  - cos 0.80 / 0.76 → Incerta (margin 0.04 < 0.05);
  - cos 0.80 / 0.70 → Sicura(A);
  - three references A, B, C, best = C by 0.1 → Sicura(C).
- AC-498 Centroid = normalized mean of the normalized reference embeddings. Two references of A,
  (1,0) and (0.6,0.8), give centroid ∝ (1.6, 0.8). A frase closer to that centroid than to B is
  Sicura(A), even when it is nearer to one single reference of B than to either single reference of A
  (fixture), which shows the centroid rule and not the max rule.
- AC-499 A non-comparable frase (zero vector, NaN, infinite, dimension ≠ the references') → Incerta,
  never an exception. A non-comparable reference is ignored in its Parlante's centroid. A Parlante
  with no comparable reference is left out of the ranking.
- AC-500 `SIMILARITA_MINIMA = 0.30` and `MARGINE_MINIMO = 0.05` are named constants, defined once,
  and marked PROVISIONAL with a KDoc pointer to ADR 0019 §4.3. `SoglieSomiglianza` rejects
  `margine <= 0`, NaN, and `minima` outside [-1, 1].

## REWORKED BLOCKS
### `diarizzatore-sherpa` (R1, doing → REWORK 2026-09-24 (ADR 0019))
- module stays `:ml-sherpa + :trascrizione:adattatori (..ml)`, plus the `:avvio` wiring edit in
  `snastro.avvio.r1.SelezioneAdattatoriMl` (file paths and catalogue list only) and
  `:modelli` `CatalogoDiarizzazione`.
- related_adrs +0017 +0019
- KEEP AC-249, AC-251
- REWRITE AC-250: the `:modelli` entries of `CatalogoDiarizzazione` must match ADR 0019 §1.7 /
  ADR 0014 exactly:
  - `segmentazione-pyannote-3.0`: unchanged asset (TAR_BZ2, sha 24615ee8…6488, 6958444 B, MIT); the
    file used is **`model.onnx`**;
  - **`embedding-nemo-titanet-small`**: FILE, `nemo_en_titanet_small.onnx`, url, sha256
    `ad4a1802…789e`, dimensioneByte `40257283`, CC-BY-4.0, attribution text.

  `embedding-wespeaker-resnet34-lm` is in neither `CatalogoDiarizzazione.voci` nor
  `SelezioneAdattatoriMl.catalogo(REALI)`. The ADR 0019 `enforced_by` is green.
- REWRITE AC-373: `numeroPersone = k` → the AHC k-cut of ADR 0019 §1.2; the result has at most k
  distinct `voceIndice`. Absent → the `SOGLIA_AHC_AUTO` cut. A `k` above the qualifying clusters
  never fails the Elaborazione. `FastClustering`'s labels are never used.
- AC-480 (gate, synthetic embeddings, no natives) The clustering on pieces with 4 blobs of 100 s,
  80 s, 70 s and 20 s of speech, k = 3 → exactly 3 `voceIndice`. The 20 s blob's pieces are assigned
  to their nearest kept centroid, and no `voceIndice` stands for it alone.
- AC-481 (gate) 2 tight blobs of 100 s each, k = 4 → exactly 2 `voceIndice`, with no exception. The
  cut keeps searching while fewer than k clusters qualify; neither blob can split into two parts of
  ≥ 60 s.
- AC-482 (gate) Qualifying minimum = min(60 000 ms, 10 % of total speech). Table: 30 min of speech →
  60 000 ms; 5 min → 30 000 ms; 0 → no clusters and an empty list of Turni.
- AC-483 (gate) Without k: cut at `SOGLIA_AHC_AUTO`, keep the qualifying clusters (at least 1), and
  assign every piece to its nearest centroid. Non-empty speech in which nothing qualifies gives
  exactly one `voceIndice`.
- AC-484 (gate) Determinism: the same input gives the same `List<Turno>`, twice in one JVM and across
  instances. Ties break on the lowest index (a fixture with equal distances).
- AC-485 (gate) Pieces:
  - a step-1 segment of 7 500 ms → 3 pieces of 2 500 ms; one of 3 000 ms → 1 piece;
  - no piece crosses a step-1 segment boundary;
  - pieces < 1 000 ms stay out of the AHC but get a `voceIndice` by nearest centroid;
  - two overlapping step-1 segments both yield Turni ([INV-7], nothing trimmed);
  - every Turno has `inizio < fine`, in ms.
- AC-486 (gate, injectable fake session / fake loader)
  - Step 1 is exactly ONE `conSessione`, and each piece embedding is ONE `conSessione`.
  - The clustering runs with the Mutex free: a probe `conSessione` from another thread, started
    while clustering, gets the Mutex at once.
  - This is ADR 0019 §1.5, amending ADR 0017 §1.1.
- AC-487 (gate) More than `PEZZI_MASSIMI_AHC = 6000` pieces → the AHC runs on every ⌈n/6000⌉-th piece
  (deterministic), and every piece still gets a `voceIndice`. Test with 13 000 synthetic pieces: it
  completes, and the subsample size is ≤ 6000.
- AC-488 [@modelli, opt-in] Stability.
  - Input: a real sample of ≥ 15 min from `sample/`, at its real speaker count k, as is and shifted by
    +10 ms and +500 ms (leading silence).
  - Measure: speech-time-weighted label agreement under the best one-to-one mapping against the
    unshifted run.
  - Pass: ≥ 0.90 for each shift. The test prints the three values.
- AC-489 [@modelli, opt-in] Reproduction and calibration on Via Roquel, k = 4.
  - Pass: 4 `voceIndice`, whose speech seconds, sorted, are each within ±15 % of 1136, 971, 803 and
    584.
  - The block records `SOGLIA_PASSO_1` and the calibrated `SOGLIA_AHC_AUTO` (sweep on Via Roquel and
    NR4, reporting counts against the real 4 and 2) in its notes, for an ADR 0019 amendment.
  - If AC-488 or AC-489 cannot pass, the block stops as BOUNCED to the architect. It never ships an
    unproven clustering.
- AC-490 (code review) These named constants are defined once, in the adapter:
  - `PEZZO_MS = 3000`, `DURATA_MINIMA_PEZZO_AHC_MS = 1000`, `DURATA_MINIMA_CLUSTER_MS = 60000`,
    `QUOTA_MINIMA_CLUSTER = 0.10`, `PEZZI_MASSIMI_AHC = 6000`;
  - `SOGLIA_AHC_AUTO` and `SOGLIA_PASSO_1`;
  - the step-1 settings (wsr 0.5, minDurationOn 0.3, minDurationOff 0.5).

  No duplicated literal.
- AC-491 (gate with a fake loader, plus [@modelli]) `EmbeddingSherpa`:
  - N `calcola` calls in one Elaborazione → 1 model load;
  - one `conSessione` per call; the Mutex is free between calls;
  - `close` releases the model, and it is closed at the end of the Elaborazione, including on
    failure;
  - at runtime, `com.k2fsa` appears only in `:ml-sherpa` (AC-245).
- Notes: the pure clustering is `internal` in `snastro.trascrizione.adattatori.ml`. Its cosine is
  its own; there is no shared math module. The experiment script is not in the repo; the settings
  come from the orchestrator if available (ADR 0019 "Points for the user" 5).

### `ml-sherpa-motore` (R1, doing)
- No AC change. Its boundary `tec-ml-sherpa` gains `EmbeddingSherpa` (B6). The code is written by
  `diarizzatore-sherpa` in `:ml-sherpa`; the note records who owns it.

### `estrattore-impronta-sherpa` (R2, todo — UNBLOCKED)
- `gated_by`: "ADR closing spike impronta-vocale-affidabilita" → **satisfied for this block by
  ADR 0019 §2** (model choice + catalogue entry). `SoglieFascia` and `BUDGET_IMPRONTA_MS` stay
  provisional config and do not gate the build. The spike itself stays OPEN.
- depends_on +diarizzatore-sherpa (it uses `EmbeddingSherpa` and the catalogue entry)
- related_adrs +0019
- KEEP AC-258, 260, 311, 406–410
- REWRITE AC-259: the model entry is `embedding-nemo-titanet-small` (ADR 0019 §1.7), the SAME
  `VoceCatalogo` as the diarizer's. There is no second entry and no second download.
- REWRITE AC-310: `modello == "embedding-nemo-titanet-small"`, constant per instance.
- AC-492 (gate with a fake loader) The extractor uses `EmbeddingSherpa`:
  - N `estrai` calls → 1 model load, and still ONE `conSessione` per `estrai` (AC-406);
  - the model is released by the adapter's `chiudi`, which `avvio-parlanti` calls at project close;
  - after `chiudi`, a new `estrai` reloads.
- AC-493 [@modelli, opt-in] 1 000 `estrai` calls over 1 000 distinct 1–10 s intervals of a real
  sample, with no Elaborazione running, take ≤ 60 s in total on the M3 Pro. The test prints the time
  (ADR 0019 §4.7 cost estimate).

### `porte-parlanti` (R2, doing → REWORK)
- AC-494 `LettoreVoci.segmenti` (B3):
  - `LettoreVociContratto` gains cases: null iff no Trascritto; every Segmento once, ordered by
    (inizio, segmentoId); `confermato` as stored; no text field (by type);
  - `LettoreVociFinta` supports it.
- AC-495 The `ClassificatoreSomiglianza` port, `Classificazione`, `SoglieSomiglianza`,
  `ClassificatoreSomiglianzaFinta` and the abstract `ClassificatoreSomiglianzaContratto` exist as
  pinned in B4. `ErroreParlanti.RiferimentiInsufficienti(registrazioneId)` is added. `:ui`
  `MessaggiErrore` needs no text for it: the button is disabled before it can happen, and the glue
  maps it to the generic message.

### `trascritto` (aggregate, R1 module, doing → REWORK; owns the ErroreTrascrizione sweep)
- invariants: +INV-26 (ADR 0019 §3). INV-8 is reworded: "(ids, intervals, text) identical; only their
  Voce and their `confermato` flag change".
- INV-26 tests:
  - `crea` → every flag false;
  - a manual `riassegna` → the moved Segmento is `confermato`;
  - `dividi` → every Segmento of S is `confermato`, and the rest keep theirs;
  - `unisci` → every flag is kept;
  - `riassegnaInBlocco` never changes a flag;
  - `confermaSegmento(s, false)` revokes.
- INV-8 test updated: after each Revisione the (id, interval, text) set is identical.
- AC-511 `riassegnaInBlocco` on a valid list:
  - every Segmento ends on its `a`, in list order;
  - one `SegmentoRiassegnato` per move, `aNuova = false`, `daRimossa` true exactly on the move that
    empties `da`;
  - an emptied Voce is removed ([INV-6]), and its number is never reused ([INV-12]);
  - no Voce is created.
- AC-512 `riassegnaInBlocco` refusals (table; state unchanged after each, compared by value):
  - Segmento missing, not on `da`, interval differs, `a` missing, Segmento `confermato` →
    `TrascrittoCambiato`;
  - `a == da`, duplicated segmentoId → `RiassegnazioneNonAmmessa`;
  - one stale entry at the END of a 10-entry list → nothing applied.
- AC-513 `confermaSegmento`: sets the flag and returns the event; the same value → `Ok(null)`; an
  unknown Segmento → `SegmentoNonTrovato`.
- AC-514 Empty list → `Ok(emptyList())`, with the state unchanged.
- AC-515 (sweep) `ErroreTrascrizione.TrascrittoCambiato` is added in ONE change, together with every
  exhaustive `when`. `:ui` `MessaggiErrore` maps it to "La trascrizione è cambiata durante il
  confronto: riprova". The pipeline's `motivo` table and the fakes compile.

### `revisione` (application-service, doing → REWORK)
- AC-516 `RiassegnaSegmentoServizio.esegui` returns `Esito<VoceId>`, the destination: the new VoceId
  when `destinazione = null`. The moved Segmento is `confermato` after the commit (repository
  round-trip through the fake). AC-80..83 are unchanged.
- AC-517 NEW command `ConfermaSegmento(registrazioneId, segmentoId, confermato)`, service
  `ConfermaSegmentoServizio`:
  - one transaction;
  - it publishes `SegmentoConfermato` exactly once on a change, and nothing on a no-op;
  - no Trascritto → `TrascrittoNonTrovato`; an unknown Segmento → `SegmentoNonTrovato`, with nothing
    written.
- `DividiVoce` needs no service change. The aggregate sets the flags; covered by the INV-26 test.

### `repository-sql-trascrizione` (doing → REWORK; depends_on +persistenza-conferma-segmento)
- AC-522 Round-trip: a Trascritto with mixed `confermato` flags is saved (delete + re-insert, as
  today) and re-read identically. A Trascritto created by `crea` saves every flag as 0. The
  replacement of ADR 0018 writes 0 everywhere.

### `trascritto-view` (read-model, doing → REWORK)
- view_shape: `segmenti: List<{segmentoId, voceId, inizioMs, fineMs, testo, confermato}>`.
- AC-523 The view exposes `confermato` per Segmento as stored. AC-167 is otherwise unchanged.

### `api-trascritto` + `lettore-voci-da-trascrizione` (doing → REWORK)
- AC-524 `LettoreVociContratto` (including the new `segmenti` cases of AC-494) passes real-on-real:
  `lettore-voci-da-trascrizione` over the Trascrizione read API, seeded through its applicazione
  commands and testFixtures fakes (CR-1). The API exposes the Segmenti with `confermato`, and never
  their text, to this adapter.

### `eventi-pubblicati` (doing → REWORK)
- REWRITE AC-14: "… un test di forma per ciascuno dei **18** eventi, inclusi … `SegmentoConfermato`".
- AC-525 `snastro.trascrizione.applicazione.eventi.SegmentoConfermato(registrazioneId, segmentoId,
  confermato: Boolean) : EventoPubblicato` exists with no other field, and is delivered to
  after-commit subscribers only after COMMIT.

### `schermata-registrazione-identificazione` (ui, R2, doing → REWORK)
- consumes_rm +trascritto-view (`confermato`). triggers +ConfermaSegmento, +RiassegnaSegmenti
  (through B8). related_adrs +0019.
- AC-526 With exactly ONE Segmento selected, the toolbar shows "Dai un nome a questa frase ▾".
  - The menu lists the `attivo` Parlanti (parlanti-attivi), then "nuovo…" (Nome field, ricorrente
    preselected, occasionale toggle, Q-7).
  - The action is absent with 0 or ≥ 2 Segmenti selected, and disabled while S3 is read-only
    (AC-454).
- AC-527 The presenter's case decision (pure; table test on fixture views) matches ADR 0019 §5:
  - (a) the Segmento is on a Voce attributed to P → `SoloConferma`;
  - (b) the Segmento is alone in its Voce → `AttribuisciVoce(thatVoce, P)`;
  - (c) P has Voci here → `Sposta(lowest voceId of P)`;
  - (d) otherwise → `NuovaVoce(P | nuovo Nome)`.

  Every case goes through `ComandiVoce.nominaFrase` (B8). The presenter never calls a command
  directly.
- AC-528 A `confermato` Segmento renders a pin marker, with the tooltip "Frase confermata:
  «Riassegna per somiglianza» non la sposta". When it is selected alone, the toolbar offers "Togli
  conferma" → `ConfermaSegmento(false)`.
- AC-529 While a `nominaFrase` is pending, that Segmento row shows the ADR 0017 pending state:
  - progress at once;
  - "In attesa dell'elaborazione…" + "Annulla" after `SOGLIA_ATTESA_VISIBILE_MS`;
  - an error (e.g. `NomeGiaInUso` on step (d)) is shown inline in plain words, and the new Voce
    appears unnamed.
- AC-530 A "Riassegna per somiglianza" button sits in the Voci panel header.
  - It is enabled iff all of these hold:
    - ≥ 2 `attivo` Parlanti have ≥ 1 frase di riferimento: a confirmed Segmento of ≥ 1 000 ms on a
      Voce attributed to them, derived from trascritto-view + identificazione-voci;
    - S3 is not read-only;
    - no Parlanti command or `nominaFrase` is pending for this Registrazione;
    - no run is in progress.
  - Disabled hint: "Dai un nome ad almeno una frase di due persone diverse".
  - Under the button: "Riferimenti: <Nomi>". If any attributed attivo Parlante has no reference, a
    line reads "Senza frase di riferimento (non toccate): <Nomi>".
- AC-531 Running (fake `AzioniSomiglianza` held by a latch, virtual time):
  - the button area shows "Confronto le frasi… n di N" with a determinate bar;
  - every editing action of the panel and of the selection toolbar is disabled, and no command is
    invoked (the fakes record zero calls);
  - playback and "▶ estratto" still work;
  - with no progress tick for `SOGLIA_ATTESA_VISIBILE_MS`, "In attesa dell'elaborazione…" is added;
  - "Annulla" is visible for the whole run.
- AC-532 "Annulla" → `AzioniSomiglianza.annulla(id)`. The panel returns to its previous state with
  no error message.
- AC-533 Result texts:
  - `Esito(3, 2)` → "3 frasi spostate, 2 incerte (rimaste dov'erano)";
  - `Esito(1, 1)` → "1 frase spostata, 1 incerta (rimasta dov'era)";
  - `Esito(0, 4)` → "Nessuna frase da spostare (4 incerte)";
  - `TrascrittoCambiato` → "La trascrizione è cambiata durante il confronto: riprova";
  - any other error → a plain message (AC-404 rule).

  The message is dismissible, and cleared by the next run or by leaving S3.
- AC-534 The run state comes from the per-project `AzioniSomiglianza.stato`. A presenter recreated
  while a run is in progress (the user left S3 and came back) shows it running again, with its
  progress.
- AC-535 When S3 turns read-only (AC-452) during a run, the presenter calls `annulla(id)`.
- AC-536 The presenter never runs the plan, the batch or `nominaFrase` on the UI thread (the AC-417
  rule; the fakes record their thread).

### `avvio-parlanti` (R2, doing → REWORK)
- depends_on +piano-riassegnazione +classificatore-somiglianza +riassegna-segmenti
  +estrattore-impronta-sherpa. related_adrs +0019.
- AC-537 `AzioniSomiglianza` (B8), implemented in the per-project scope:
  - `avvia` runs `PianoRiassegnazioneQuery.calcola` on a background dispatcher, via
    `runInterruptible`;
  - if `spostamenti` is non-empty, it runs `RiassegnaSegmenti` (mapped 1:1). If empty, no command is
    sent;
  - then it publishes `Esito(spostamenti.size, incerte)`;
  - there is at most one run per Registrazione, and a second `avvia` while running is ignored;
  - `annulla` interrupts the computation, and nothing is written;
  - the final transaction is not interrupted once started;
  - closing the project cancels and joins, bounded like `CollaboratoriR1.ferma`, with no DB access
    after `chiudi` (AC-420 rule);
  - `AvviaElaborazione` for the same Registrazione (Ritrascrivi queued) cancels the run.
- AC-538 E2E on databaseInMemoria with fake ML ports (the extractor returns a vector per interval
  from a table).
  - Setup: Voce 1 attributed to Anna, with one confirmed 2 s Segmento; Voce 2 attributed to Marco,
    with one confirmed Segmento; Voce 3 unattributed, with 4 Segmenti (2 Anna-like, 1 Marco-like, 1
    ambiguous); Voce 4 attributed to Luca, with no confirmed Segmento.
  - Run. Expected:
    - Voce 3's Anna-like Segmenti are on Voce 1, and its Marco-like one on Voce 2;
    - the ambiguous one is still on Voce 3;
    - Voce 4 is untouched;
    - no confirmed Segmento moved;
    - the summary is `Esito(3, 1)`;
    - exactly one `Documento` regeneration and one `RiallineaImpronte` of that Registrazione after
      commit.
  - Run again → zero commands and `Esito(0, 1)`.
- AC-539 E2E stale plan: during the computation (fake extractor held), a manual `RiassegnaSegmento`
  commits a planned Segmento elsewhere. On release the batch → `TrascrittoCambiato`: nothing from
  the batch is written, and the state is the error.
- AC-540 E2E "Dai un nome a una frase", case (d):
  - `nominaFrase(NuovaVoce(nuovo "Dario"))` on a Segmento of a 3-Segmento unattributed Voce → a new
    Voce holds that Segmento, `confermato`, attributed to the new ricorrente "Dario" with one print
    row;
  - the Documento shows "Dario" on that line;
  - with "Dario" already used by an attivo, the new Voce exists unnamed, the error is
    `NomeGiaInUso`, and nothing else is written.
- AC-541 REALI wiring:
  - `EstrattoreImprontaSherpa(EmbeddingSherpa on embedding-nemo-titanet-small)` +
    `DecodificatoreAudioFfmpeg`, `proposte = true` [default §P-4];
  - `ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO))`;
  - `EstrattoreImprontaAssente` / `DecodificatoreAudioAssente` are removed;
  - the extractor's `chiudi` is called at project close;
  - `SegmentoConfermato` → one `Cambiamento(registrazioneId)` after commit;
  - existing `nessun-estrattore` print rows are re-derived by `RiallineaTutteLeImpronte` at the next
    open (AC-316 test with a row whose modello is `nessun-estrattore`).
- KEEP every other AC.

### `benchmark-elaborazione` (R1, todo)
- related_adrs +0019. `depends_on` is unchanged: `diarizzatore-sherpa` is already there, and this
  block runs after its rework.
- AC-542 [opt-in, fuori gate] Besides the per-phase times (AC-261), the benchmark prints the
  `diarizzazione` sub-times:
  - step 1 (the one native hold, i.e. the worst-case Mutex wait, ADR 0019 §1.5);
  - the piece embeddings;
  - clustering + assignment.

  Pass condition AC-261: ≤ 600 s per 60 min. ADR 0019 estimates ≈ 346 s under load.

## Not changed
- `modelli-provisioning`: the mechanics (download, SHA, FILE install) are unchanged. The new entry is
  data in `CatalogoDiarizzazione` (owner `diarizzatore-sherpa`, AC-250). S5 shows the 40 MB entry as
  missing at the next start. The orphaned `embedding-wespeaker-resnet34-lm/` folder is not cleaned
  (LOW; no AC).
- `schermata-modelli` (S5): the licence list is derived from the catalogue, so WeSpeaker disappears
  and TitaNet appears with no code change.
- `allineatore`: ADR 0015 is unchanged. It merges consecutive same-voice pieces (gap 0 < 1 000 ms).
- `revisione-policy`, `abbonato-revisione-parlanti`, `riallinea-impronte`, `abbonato-riallineamento-impronte`,
  `abbonato-documento`, `rigenerazione-documento`: they consume `SegmentoRiassegnato` unchanged. The
  batch is covered end-to-end by AC-538.
- `proposta`, `proposta-unione`, `confronto-impronte`: unchanged. `SoglieFascia` stays provisional.
- `avvio-composizione`: the wiring edit of `SelezioneAdattatoriMl` is carried by
  `diarizzatore-sherpa`.
- `tec-diarizzatore` / `esegui-elaborazione`: no change (B7 is a note).

## Texts to fold by their owners (not written by the architect)
- **tactical-model.md § Trascrizione:**
  - [INV-8] is reworded (above);
  - add [INV-26] (ADR 0019 §3);
  - commands: `ConfermaSegmento` (utente: "Dai un nome a una frase" case (a), "Togli conferma"), and
    `RiassegnaSegmenti` (utente, through "Riassegna per somiglianza"; ONE transaction);
  - `RiassegnaSegmento` returns the destination and confirms the moved Segmento;
  - event `SegmentoConfermato` → read-model Trascritto (view refresh) only.
- **tactical-model.md § Parlanti:**
  - add [INV-27] (ADR 0019 §4.4);
  - read-model `PianoRiassegnazione` (computed, never stored, consumer: S3 via the `:avvio` glue);
  - `Frase di riferimento` is derived (a confirmed Segmento of ≥ 1 s on a Voce attributed to an
    attivo P);
  - the `Proposta` is fed by the real extractor (TitaNet-small) with provisional `SoglieFascia`.
- **context-map.md ubiquitous language:**
  - Trascrizione — **Segmento confermato** ("placed or confirmed on its Voce by the user; never moved
    automatically");
  - Parlanti — **Frase di riferimento**, **Riassegna per somiglianza**, **frase incerta**.
  - The `impronta-vocale-affidabilita` spike line is annotated by the architect (partially answered,
    model chosen, ADR 0019).
- **UI/ux-proposal.md S3:** the entry point, the pin marker, "Togli conferma", the panel-header button
  with its states and texts (ADR 0019 §6).
