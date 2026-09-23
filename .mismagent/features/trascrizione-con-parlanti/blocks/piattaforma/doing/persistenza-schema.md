---
id: "persistenza-schema"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 2
release: "R0"
module: ":persistenza"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0009"
  - "0012"
  - "0014"
---
# persistenza-schema — Schema SQLDelight v1, factory del driver, UnitaDiLavoroSql

## What to do
Derived owner of the single persistence schema (rule 11): one .sq per table (progetto, registrazione, elaborazione, trascritto, voce, segmento, parlante, impronta_vocale, attribuzione) with named queries, the three ADR 0007 partial unique indexes each on ONE line, PRIMARY KEY attribuzione(registrazione_id, voce_id), UNIQUE impronta_vocale(parlante_id, registrazione_id, voce_id), impronta_vocale.sorgente_impronta TEXT NOT NULL + modello_impronta TEXT NOT NULL (ADR 0009 Amendment (b), straight into the unreleased v1), the compare-and-set print UPDATE and the print-metadata reads (AC-267), schema snapshot 1.db, apriDatabaseProgetto(cartella) with WAL + foreign_keys=ON + secure_delete=ON, refusal of a newer user_version, UnitaDiLavoroSql, and the testFixture databaseInMemoria().

REWORK 2026-09-24 (ADR 0014): add a FORWARD-ONLY migration after 1.sqm (user_version +1) adding elaborazione.numero_persone INTEGER NULL CHECK (numero_persone BETWEEN 1 AND 10), update elaborazione.sq insert/select, regenerate the migration snapshot; existing rows stay NULL (AC-377).

Note: ADR 0007 and ADR 0009 presence rules become exigible when this block is merged (exigible_from: persistenza-schema). AMENDED 2026-09-23 (ADR 0009/0012 Amendment (b)): schema v1 is UNRELEASED — the two columns go straight into v1, no migration; regenerate the committed 1.db snapshot (AC-8 stays green). AMENDED 2026-09-24 (ADR 0014, ADR 0006): R0 is released, so the new column is a FORWARD-ONLY migration (AC-377), never an edit of the existing schema/1.sqm.

## Tasks
- AC-8 verifySqlDelightMigration è verde con lo snapshot 1.db committato
- AC-9 I tre indici unici parziali di ADR 0007 esistono, ciascuno su una sola riga (regola di presenza verde)
- AC-10 Il driver apre il DB con journal WAL, foreign_keys=ON e secure_delete=ON (pragma letti nel test)
- AC-11 UnitaDiLavoroSql passa UnitaDiLavoroContratto: rollback su Errore e su eccezione
- AC-12 Un DB con versione di schema più recente dell'app viene rifiutato con un errore chiaro, senza modificarlo
- AC-13 attribuzione ha chiave (registrazione_id, voce_id) e impronta_vocale rifiuta una seconda riga con stessi (parlante_id, registrazione_id, voce_id); impronta_vocale ha le colonne sorgente_impronta TEXT NOT NULL e modello_impronta TEXT NOT NULL (un inserimento senza una delle due è rifiutato)
- AC-267 impronta_vocale.sq espone: l'UPDATE compare-and-set di UNA riga (SET impronta, sorgente_impronta, modello_impronta WHERE parlante_id, registrazione_id, voce_id AND sorgente_impronta = :attesa AND modello_impronta = :atteso — nessun INSERT) e le letture dei metadati (parlante_id, registrazione_id, voce_id, sorgente_impronta, modello_impronta, senza BLOB) per registrazione e per progetto; test SQL: l'UPDATE con valori attesi cambiati tocca 0 righe
- AC-377 (ex AC-NP10, schema) Migrazione forward-only (nuovo file di migrazione dopo 1.sqm, user_version +1): elaborazione.numero_persone INTEGER NULL CHECK (numero_persone BETWEEN 1 AND 10); le righe esistenti restano con NULL (test: DB alla versione precedente con un'Elaborazione → migrato → numero_persone NULL); un INSERT con 0 o 11 è rifiutato; verifySqlDelightMigration verde con il nuovo snapshot

## Dependencies
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
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable forever
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012, 0014 (.mismagent/decisions/); ADR 0006/0007/0009/0012, dev-architecture-app.md #repository, features/trascrizione-con-parlanti/tactical-model.md (all aggregates).
