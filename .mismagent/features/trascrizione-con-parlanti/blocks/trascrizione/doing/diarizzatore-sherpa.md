---
id: "diarizzatore-sherpa"
type: "adapter"
context: "trascrizione"
side: "app"
wave: 12
release: "R1"
module: ":ml-sherpa (EmbeddingSherpa) + :trascrizione:adattatori (..ml) + :modelli (CatalogoDiarizzazione) + :avvio (snastro.avvio.r1.SelezioneAdattatoriMl wiring edit)"
consumes:
  - "kernel-pl"
  - "tec-diarizzatore"
  - "tec-ml-sherpa"
depends_on:
  - "avvio-composizione"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0012"
  - "0014"
  - "0016"
  - "0017"
  - "0019"
gated_by:
  - "ADR closing spike scelta-diarizzatore — satisfied: ADR 0014 (accepted 2026-09-23)"
---
# diarizzatore-sherpa — Diarizzatore reale su sherpa-onnx

## What to do
Real Diarizzatore adapter with the model chosen by the spike ADR (catalogue entry URL + SHA-256 + licence added to :modelli in the same block); registered in :avvio's adapter-selection config (W12 blocks merged serially: they share that config file).

REWORK 2026-09-24 (ADR 0019): replace the sherpa FastClustering(numClusters = k) diarization with ADR 0019 §1.2 — step 1 sherpa (seg-3.0 fp32 model.onnx + ResNet34-LM, FastClustering(-1, 0.2), wsr 0.5, on 0.3 / off 0.5; its labels discarded), <= 3 s equal pieces, one TitaNet-small embedding per piece through the new :ml-sherpa EmbeddingSherpa (model cached, one conSessione per call), pure-Kotlin average-linkage (by piece count) cosine AHC over pieces >= 1.5 s with the k-cut or the 0.5 auto cut and min(60 s, 10 %) qualifying clusters, duration-weighted centroids and nearest-centroid assignment of every piece; the Mutex is held only for step 1 and per piece embedding. Also CatalogoDiarizzazione (+ embedding-nemo-titanet-small; segmentation file model.onnx; ResNet34-LM kept) and the :avvio SelezioneAdattatoriMl wiring (three model files). Exact settings: ADR 0019 §1.9 'Parametri misurati'; reference scripts diar2/{common,clus,stage1,stage2}.py handed over by the orchestrator. Tests AC-250 and AC-373 rewritten, AC-480..AC-491; the ADR 0019 enforced_by becomes exigible at merge.

Note: AMENDED 2026-09-24 (ADR 0014): runtime config and catalogue entries are pinned by ADR 0014; the adapter implements diarizza(c, numeroPersone). AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): the diarization is replaced by ADR 0019 §1.2 (step 1 sherpa seg fp32 + ResNet34-LM over-split, labels discarded; <= 3 s pieces; TitaNet-small piece embeddings through the new :ml-sherpa EmbeddingSherpa, owned HERE as its first user; pure-Kotlin internal AHC in snastro.trascrizione.adattatori.ml with its own cosine; nearest-centroid assignment); exact settings in ADR 0019 §1.9 'Parametri misurati'. Reference implementation: the experiment scripts diar2/{common,clus,stage1,stage2}.py of session 1f80eddf's scratch, handed to the worker by the orchestrator (not committed). Also carries the :modelli CatalogoDiarizzazione entry (AC-250) and the :avvio SelezioneAdattatoriMl wiring edit (three model files; avvio-composizione is not reopened). ADR 0019 enforced_by is exigible_from this block's merge.

## Tasks
- AC-249 [@modelli] DiarizzatoreContratto passa contro l'adattatore reale su un campione di sample/
- AC-250 (REWRITTEN 2026-09-24, ADR 0019) The :modelli entries of CatalogoDiarizzazione match ADR 0019 §1.7 / ADR 0014 exactly: segmentazione-pyannote-3.0 unchanged asset (TAR_BZ2, sha 24615ee8…6488, 6958444 B, MIT) whose file used is model.onnx (fp32); embedding-nemo-titanet-small FILE nemo_en_titanet_small.onnx, url, sha256 ad4a1802…789e, dimensioneByte 40257283, CC-BY-4.0, attribution text; embedding-wespeaker-resnet34-lm unchanged and kept (step 1). CatalogoDiarizzazione.voci = [segmentazione, resnet34-lm, titanet-small]; no main source of :avvio or :trascrizione:adattatori references model.int8.onnx; the ADR 0019 enforced_by is green
- AC-373 (REWRITTEN 2026-09-24, ADR 0019) numeroPersone = k → the AHC k-cut of ADR 0019 §1.2 and the result has at most k distinct voceIndice; absent → the SOGLIA_AHC_AUTO = 0.5 cut; a k above the qualifying clusters never fails the Elaborazione; step 1 is configured with numClusters = -1, threshold = 0.2 and its labels are never used (test on the built ConfigDiarizzazione)
- AC-251 Tutte le risorse native sono rilasciate a fine uso (use {})
- AC-480 (gate, synthetic embeddings, no natives) Clustering on pieces with 4 blobs of 100 s, 80 s, 70 s and 20 s of speech, k = 3 → exactly 3 voceIndice; the 20 s blob's pieces are assigned to their nearest kept centroid and no voceIndice stands for it alone
- AC-481 (gate) 2 tight blobs of 100 s each, k = 4 → exactly 2 voceIndice, no exception: the cut keeps searching while fewer than k clusters qualify and neither blob can split into two parts of >= 60 s
- AC-482 (gate) Qualifying minimum = min(60 000 ms, 10 % of total speech): 30 min of speech → 60 000 ms; 5 min → 30 000 ms; 0 → no clusters and an empty list of Turni
- AC-483 (gate) Without k: cut at SOGLIA_AHC_AUTO, keep the qualifying clusters (at least 1) and assign every piece to its nearest centroid; non-empty speech in which nothing qualifies gives exactly one voceIndice
- AC-484 (gate) Determinism: the same input gives the same List<Turno>, twice in one JVM and across instances; ties break on the lowest index — the merge picks the lowest (i, j) row-major and the assignment the lowest centroid index (fixture with equal distances; ADR 0019 §1.9)
- AC-485 (gate) Pieces and clustering exactly as ADR 0019 §1.9: a step-1 segment of 7 500 ms → 3 pieces of 2 500 ms, one of 3 000 ms → 1 piece (max(1, ceil(d / 3000)) equal parts, no overlap between them); no piece crosses a step-1 segment boundary; piece embeddings normalized as v / (‖v‖ + 1e-9); pieces < 1 500 ms stay out of the AHC but get a voceIndice by nearest centroid; the average linkage is weighted by piece COUNT (UPGMA), not by duration (fixture where the two give different merge orders follows the count); the k-rule raises the cut from m = k until >= k clusters qualify, then keeps the k with the most qualifying speech summed over pieces >= 1.5 s only; centroids are the duration-weighted sum of normalized embeddings, L2-normalized (fixture where weighting changes the winner); two overlapping step-1 segments both yield Turni (INV-7, nothing trimmed); every Turno has inizio < fine, in ms
- AC-486 (gate, injectable fake session / fake loader) Step 1 is exactly ONE conSessione and each piece embedding is ONE conSessione; the clustering runs with the Mutex free: a probe conSessione from another thread, started while clustering, gets the Mutex at once (ADR 0019 §1.5, amending ADR 0017 §1.1)
- AC-487 (gate) More than PEZZI_MASSIMI_AHC = 6000 pieces → the AHC runs on every ceil(n/6000)-th piece (deterministic) and every piece still gets a voceIndice; test with 13 000 synthetic pieces: it completes and the subsample size is <= 6000
- AC-488 [@modelli, opt-in] Stability on Via Roquel, k = 4: the recording as is and with 160 / 766 / 8000 leading zero samples (+10 / +47.9 / +500 ms); frame-level (10 ms, round((s − offset)·100), a later Turno overwrites an earlier one, only frames labelled in both runs) agreement under the best one-to-one mapping (Hungarian) over all 6 pairs, as the experiment's stability (ADR 0019 §1.9); pass: mean >= 0.92 and min >= 0.90 (measured 0.940 / 0.928); also PRINTS NR4 at k = 2 (measured 0.72), information only
- AC-489 [@modelli, opt-in] Reproduction: Via Roquel k = 4 → 4 voceIndice whose speech seconds, sorted, are each within ±15 % of 1136, 971, 803 and 584; auto (SOGLIA_AHC_AUTO = 0.5): Via Roquel gives 4 voices of >= 60 s and NR4 gives 3. If AC-488 or AC-489 cannot pass the block stops BOUNCED to the architect; it never ships an unproven clustering
- AC-490 (code review) Named constants defined once in the adapter: PEZZO_MS = 3000, DURATA_MINIMA_PEZZO_AHC_MS = 1500, DURATA_MINIMA_CLUSTER_MS = 60000, QUOTA_MINIMA_CLUSTER = 0.10, PEZZI_MASSIMI_AHC = 6000, SOGLIA_AHC_AUTO = 0.5, SOGLIA_PASSO_1 = 0.2 and the step-1 settings (wsr 0.5, minDurationOn 0.3, minDurationOff 0.5); no duplicated literal
- AC-491 (gate with a fake loader, plus [@modelli]) EmbeddingSherpa: N calcola in one Elaborazione → 1 model load; one conSessione per call and the Mutex free between calls; close releases the model and is called at the end of the Elaborazione, also on failure; at runtime com.k2fsa appears only in :ml-sherpa (AC-245)

## Dependencies
- **GATED — not ready until:** ADR closing spike scelta-diarizzatore — satisfied: ADR 0014 (accepted 2026-09-23)
- Blocks built first: `avvio-composizione` (wave 10)
- **kernel-pl** (consumed/implemented) — owner `kernel`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoId`: @JvmInline value class(valore: String) — UUID
    - `RegistrazioneId`: @JvmInline value class(valore: String) — UUID
    - `ElaborazioneId`: @JvmInline value class(valore: String) — UUID
    - `ParlanteId`: @JvmInline value class(valore: String) — UUID
    - `VoceId`: @JvmInline value class(numero: Int) — equals the n of 'Voce n'
    - `SegmentoId`: @JvmInline value class(numero: Int)
    - `VoceRef`: data class(registrazioneId: RegistrazioneId, voceId: VoceId)
    - `IntervalloMs`: data class(inizioMs: Long, fineMs: Long) — require 0 <= inizioMs < fineMs; ordered numerically
    - `RiferimentoAudio`: @JvmInline value class(percorsoRelativo: String)
    - `CampioniAudio`: class(campioni: FloatArray) — 16 kHz mono float, explicit equals/hashCode (CR-5)
    - `EstrattoRef`: data class(registrazioneId: RegistrazioneId, intervalli: List<IntervalloMs>) — non-empty, ordered by inizioMs, total duration <= 10 000 ms, played as a sequence
    - `Esito`: sealed interface Esito<out T> { Ok<T>(valore: T); Errore(errore: ErroreDominio) } + poi / mappa / seErrore
    - `ErroreDominio`: interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errore<Contesto> : ErroreDominio in file Errori<Contesto>.kt (ADR 0003 amended)
    - `EventoDominio`: marker interface for domain events (returned by aggregate methods)
    - `EventoPubblicato`: marker interface for published events (Published Language, <ctx>:applicazione.eventi)
    - `Creato`: data class Creato<A, E>(aggregato: A, evento: E)
    - `GeneratoreId`: interface { fun nuovo(): String } — UUID v4; testFixtures GeneratoreIdFinto: 'id-1', 'id-2', …
    - `UnitaDiLavoro`: interface { fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — rolls back when the block returns Errore or throws
    - `DispatcherEventi`: interface { fun pubblica(evento: EventoPubblicato) } + registration of AbbonatoSincrono / AbbonatoDopoCommit
    - `RicostituzioneDaPersistenza`: @RequiresOptIn(level = ERROR) annotation class — only ..adattatori.persistenza.. opts in (CR-15)
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
- **tec-diarizzatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Diarizzatore`: interface { fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> } — numeroPersone = k → at most k distinct voceIndice (may be fewer); null → automatic clustering (ADR 0014 rules; clustering per ADR 0019 §1.2: with k above the real count it tends to split one voice, never to fail); no sherpa type crosses the port (ADR 0004)
    - `NumeroPersone`: see agg-elaborazione — :trascrizione:dominio VO, 1..10 (the pipeline passes the Elaborazione's own value)
    - `Turno`: data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index
  - keys (minting rules):
    - `voceIndice`: minted by the Diarizzatore adapter per run — transient, NEVER persisted; the trascritto aggregate maps it to VoceId by first appearance
- **tec-ml-sherpa** (consumed/implemented) — owner `ml-sherpa-motore`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.ml.MotoreSherpa`: fun caricaNativi(); fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T — AutoCloseable released after use; holds ONE process-wide FAIR Mutex for one native session (ONE native call at a time); the wait is INTERRUPTIBLE: an interrupt while waiting → InterruptedException, and no session, no native load and no uso run; not reentrant; the Mutex is released on return or exception; every adapter holds it for ONE port call only (ADR 0017 §1)
    - `snastro.ml.EmbeddingSherpa`: class(motore: MotoreSherpa, percorsoModello: Path, threadIntraOp: Int) : AutoCloseable { fun calcola(campioni: FloatArray): FloatArray; override fun close() } — ONE conSessione per calcola; the model is loaded at the first call, cached across calls and released by close (the RiconoscitoreSherpa pattern); Mutex rules per ADR 0017 §1; code owned by diarizzatore-sherpa (first user), shared by estrattore-impronta-sherpa (ADR 0019 §1.4)
    - `ConfigSessione`: data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = "cpu")

Sources: ADRs 0002, 0003, 0004, 0008, 0012, 0014, 0016, 0017, 0019 (.mismagent/decisions/); spike scelta-diarizzatore, ADR 0004/0008/0014.
