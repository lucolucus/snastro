---
id: "estrattore-impronta-sherpa"
type: "adapter"
context: "parlanti"
side: "app"
wave: 12
release: "R2"
module: ":ml-sherpa + :parlanti:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-estrattore-impronta"
  - "tec-ml-sherpa"
depends_on:
  - "avvio-parlanti"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0009"
  - "0012"
  - "0017"
gated_by:
  - "ADR closing spike impronta-vocale-affidabilita"
---
# estrattore-impronta-sherpa — EstrattoreImpronta reale su sherpa-onnx

## What to do
Real EstrattoreImpronta adapter with the model chosen by the spike ADR (catalogue entry URL + SHA-256 + licence added to :modelli in the same block); `modello` = that catalogue id; takes the native Mutex INSIDE estrai; registered in :avvio's adapter-selection config (W12 blocks merged serially: they share that config file). Also sets the calibrated SoglieFascia values in the config.

Note: AMENDED 2026-09-23 (ADR 0012 Amendment (b) points 2, 5): supplies EstrattoreImpronta.modello (catalogue id) and takes the native Mutex inside estrai; callers guarantee no open transaction. RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): wires itself into avvio-parlanti (R2), no longer the R1 composition. AMENDED 2026-09-24 (ADR 0017, manifest delta 2026-09-24-mutex): AC-406..AC-410; this block also carries the :ml-sherpa MotoreSherpa change (lockInterruptibly on the fair Mutex, AC-408/409) and the riconoscitore-sherpa Mutex regression (AC-410, gate test in :ml-sherpa). Its own gate impronta-vocale-affidabilita stays OPEN.

## Tasks
- AC-258 [@modelli] EstrattoreImprontaContratto passa contro l'adattatore reale su un campione di sample/
- AC-259 La voce del modello nel catalogo ha URL, SHA-256 e licenza come da ADR dello spike
- AC-260 Tutte le risorse native sono rilasciate a fine uso (use {})
- AC-310 modello = l'id della voce di catalogo del modello di embedding (ADR 0008), costante per l'istanza e uguale a quello registrato in :modelli
- AC-311 Il Mutex nativo è acquisito DENTRO estrai (mai dal chiamante, mai dentro una transazione) e rilasciato anche in caso di eccezione
- AC-406 (ADR 0017 §1) estrai apre esattamente UNA conSessione per chiamata, per UNA sola impronta, e non la trattiene dopo il ritorno: dopo che estrai ritorna, una conSessione da un altro thread ottiene subito il Mutex
- AC-407 (ADR 0017 §1.5) Se il thread è interrotto durante l'estrazione nativa, estrai lancia InterruptedException dopo la chiusura della sessione e non restituisce alcuna Impronta — test del gate con un estrattore nativo finto
- AC-408 (ADR 0017; modifica a MotoreSherpa di :ml-sherpa, portata qui perché vive nel modulo di questo blocco e serve solo a R2) conSessione attende con lockInterruptibly(): A tiene il Mutex, B attende in conSessione, B è interrotto → B riceve InterruptedException, l'uso di B non gira mai e nessun nativo è caricato; A rilascia normalmente; AC-401 (serializzazione, rilascio su eccezione) resta verde
- AC-409 (ADR 0017 §1.3) Il Mutex è equo: A lo tiene, poi B e C si accodano → lo ottengono nell'ordine B, C; la pipeline che lo richiede di nuovo dopo il suo rilascio si accoda dietro un'estrazione in attesa
- AC-410 (regressione ADR 0017, portata da riconoscitore-sherpa che non è riaperto) Il Mutex è libero tra due chiamate riconosci: con il MotoreRiconoscimento finto iniettabile e un MotoreSherpa con loader finto, dopo il ritorno di riconosci una conSessione da un altro thread ottiene subito il Mutex e il modello resta in cache (nessun secondo caricamento)

## Dependencies
- **GATED — not ready until:** ADR closing spike impronta-vocale-affidabilita
- Blocks built first: `avvio-parlanti` (wave 11)
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
    - `ConfigSessione`: data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = "cpu")

Sources: ADRs 0002, 0003, 0004, 0008, 0009, 0012, 0017 (.mismagent/decisions/); spike impronta-vocale-affidabilita, ADR 0004/0008.
