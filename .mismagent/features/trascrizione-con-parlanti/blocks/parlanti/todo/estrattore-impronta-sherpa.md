---
id: "estrattore-impronta-sherpa"
type: "adapter"
context: "parlanti"
side: "app"
wave: 13
release: "R2"
module: ":ml-sherpa + :parlanti:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-estrattore-impronta"
  - "tec-ml-sherpa"
depends_on:
  - "diarizzatore-sherpa"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0009"
  - "0012"
  - "0017"
  - "0019"
gated_by:
  - "ADR closing spike impronta-vocale-affidabilita — satisfied for this block (model choice + catalogue entry): ADR 0019 §2 (accepted 2026-09-24); SoglieFascia and BUDGET_IMPRONTA_MS stay PROVISIONAL configuration and do not gate the build; the spike itself stays OPEN (partially answered)"
---
# estrattore-impronta-sherpa — EstrattoreImpronta reale su sherpa-onnx

## What to do
Real EstrattoreImpronta adapter (UPDATED 2026-09-24, ADR 0019 §2): EstrattoreImprontaSherpa over the :ml-sherpa EmbeddingSherpa written by the diarizzatore-sherpa rework, with the model chosen by ADR 0019 — embedding-nemo-titanet-small, the SAME catalogue entry and download as the diarizer's (no second entry); `modello` = that id; ONE conSessione per estrai with the native Mutex taken INSIDE estrai; the model cached across calls and released by chiudi at project close. SoglieFascia stay PROVISIONAL injected configuration (the spike stays open for their calibration, not a gate). The REALI wiring into :avvio is done by the avvio-parlanti rework (AC-541), not by this block.

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b) points 2, 5): supplies EstrattoreImpronta.modello (catalogue id) and takes the native Mutex inside estrai; callers guarantee no open transaction. RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): wires itself into avvio-parlanti (R2), no longer the R1 composition. AMENDED 2026-09-24 (ADR 0017, manifest delta 2026-09-24-mutex): AC-406..AC-410; this block also carries the :ml-sherpa MotoreSherpa change (lockInterruptibly on the fair Mutex, AC-408/409) and the riconoscitore-sherpa Mutex regression (AC-410, gate test in :ml-sherpa). Its own gate impronta-vocale-affidabilita stays OPEN. AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): GATE SATISFIED for the model choice by ADR 0019 §2 — TitaNet-small (embedding-nemo-titanet-small, shared with the diarizer, via EmbeddingSherpa); SoglieFascia stay provisional config (ADR 0004: injected), not a gate. depends_on is now diarizzatore-sherpa (EmbeddingSherpa + the catalogue entry); the old edge to avvio-parlanti is REVERSED: this block no longer wires itself — avvio-parlanti's rework does the REALI wiring (AC-541). Existing print rows of model 'nessun-estrattore' are re-derived by RiallineaTutteLeImpronte (AC-316); no migration.

## Tasks
- AC-258 [@modelli] EstrattoreImprontaContratto passa contro l'adattatore reale su un campione di sample/
- AC-259 (REWRITTEN 2026-09-24, ADR 0019) The model entry is embedding-nemo-titanet-small (ADR 0019 §1.7), the SAME VoceCatalogo as the diarizer's: no second entry, no second download
- AC-260 Tutte le risorse native sono rilasciate a fine uso (use {})
- AC-310 (REWRITTEN 2026-09-24, ADR 0019) modello == 'embedding-nemo-titanet-small', constant per instance and equal to the id registered in :modelli
- AC-311 Il Mutex nativo è acquisito DENTRO estrai (mai dal chiamante, mai dentro una transazione) e rilasciato anche in caso di eccezione
- AC-406 (ADR 0017 §1) estrai apre esattamente UNA conSessione per chiamata, per UNA sola impronta, e non la trattiene dopo il ritorno: dopo che estrai ritorna, una conSessione da un altro thread ottiene subito il Mutex
- AC-407 (ADR 0017 §1.5) Se il thread è interrotto durante l'estrazione nativa, estrai lancia InterruptedException dopo la chiusura della sessione e non restituisce alcuna Impronta — test del gate con un estrattore nativo finto
- AC-408 (ADR 0017; modifica a MotoreSherpa di :ml-sherpa, portata qui perché vive nel modulo di questo blocco e serve solo a R2) conSessione attende con lockInterruptibly(): A tiene il Mutex, B attende in conSessione, B è interrotto → B riceve InterruptedException, l'uso di B non gira mai e nessun nativo è caricato; A rilascia normalmente; AC-401 (serializzazione, rilascio su eccezione) resta verde
- AC-409 (ADR 0017 §1.3) Il Mutex è equo: A lo tiene, poi B e C si accodano → lo ottengono nell'ordine B, C; la pipeline che lo richiede di nuovo dopo il suo rilascio si accoda dietro un'estrazione in attesa
- AC-410 (regressione ADR 0017, portata da riconoscitore-sherpa che non è riaperto) Il Mutex è libero tra due chiamate riconosci: con il MotoreRiconoscimento finto iniettabile e un MotoreSherpa con loader finto, dopo il ritorno di riconosci una conSessione da un altro thread ottiene subito il Mutex e il modello resta in cache (nessun secondo caricamento)
- AC-492 (gate with a fake loader) The extractor uses EmbeddingSherpa: N estrai → 1 model load and still ONE conSessione per estrai (AC-406); the model is released by the adapter's chiudi, which avvio-parlanti calls at project close; after chiudi a new estrai reloads
- AC-493 [@modelli, opt-in] 1 000 estrai over 1 000 distinct 1–10 s intervals of a real sample, with no Elaborazione running, take <= 60 s in total on the M3 Pro; the test prints the time (ADR 0019 §4.7 cost estimate)

## Dependencies
- **GATED — not ready until:** ADR closing spike impronta-vocale-affidabilita — satisfied for this block (model choice + catalogue entry): ADR 0019 §2 (accepted 2026-09-24); SoglieFascia and BUDGET_IMPRONTA_MS stay PROVISIONAL configuration and do not gate the build; the spike itself stays OPEN (partially answered)
- Blocks built first: `diarizzatore-sherpa` (wave 12)
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
- **tec-estrattore-impronta** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `EstrattoreImpronta`: interface { val modello: String /* catalogue id of the embedding model (ADR 0008), stored as impronta_vocale.modello_impronta */; fun estrai(c: CampioniAudio): Impronta } — NEVER called while a UnitaDiLavoro transaction is open; the adapter serializes native use with the pipeline by taking the native Mutex INSIDE estrai (ADR 0012 Amendment (b) points 2, 5); ONE conSessione per estrai call, for ONE print, never kept after return; an interrupt (cancellation) while waiting for the Mutex or during the native extraction → InterruptedException after the session closes, and no Impronta (ADR 0017 §1.2, §1.5)
    - `EstrattoreImprontaFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when estrai is invoked while transazioneAperta; modello configurable (default "finto")
    - `Impronta`: see agg-parlante (parlanti:dominio)
- **tec-ml-sherpa** (consumed/implemented) — owner `ml-sherpa-motore`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.ml.MotoreSherpa`: fun caricaNativi(); fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T — AutoCloseable released after use; holds ONE process-wide FAIR Mutex for one native session (ONE native call at a time); the wait is INTERRUPTIBLE: an interrupt while waiting → InterruptedException, and no session, no native load and no uso run; not reentrant; the Mutex is released on return or exception; every adapter holds it for ONE port call only (ADR 0017 §1)
    - `snastro.ml.EmbeddingSherpa`: class(motore: MotoreSherpa, percorsoModello: Path, threadIntraOp: Int) : AutoCloseable { fun calcola(campioni: FloatArray): FloatArray; override fun close() } — ONE conSessione per calcola; the model is loaded at the first call, cached across calls and released by close (the RiconoscitoreSherpa pattern); Mutex rules per ADR 0017 §1; code owned by diarizzatore-sherpa (first user), shared by estrattore-impronta-sherpa (ADR 0019 §1.4)
    - `ConfigSessione`: data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = "cpu")

Sources: ADRs 0002, 0003, 0004, 0008, 0009, 0012, 0017, 0019 (.mismagent/decisions/); spike impronta-vocale-affidabilita, ADR 0004/0008, ADR 0019 §1.4, §1.7, §2.
