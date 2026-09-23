---
id: "persistenza-schema"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 2
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
---
# persistenza-schema — Schema SQLDelight v1, factory del driver, UnitaDiLavoroSql

## What to do
Derived owner of the single persistence schema (rule 11): one .sq per table (progetto, registrazione, elaborazione, trascritto, voce, segmento, parlante, impronta_vocale, attribuzione) with named queries, the three ADR 0007 partial unique indexes each on ONE line, PRIMARY KEY attribuzione(registrazione_id, voce_id), UNIQUE impronta_vocale(parlante_id, registrazione_id, voce_id), schema snapshot 1.db, apriDatabaseProgetto(cartella) with WAL + foreign_keys=ON + secure_delete=ON, refusal of a newer user_version, UnitaDiLavoroSql, and the testFixture databaseInMemoria().

Note: ADR 0007 and ADR 0009 presence rules become exigible when this block is merged (exigible_from: persistenza-schema).

## Tasks
- AC-8 verifySqlDelightMigration è verde con lo snapshot 1.db committato
- AC-9 I tre indici unici parziali di ADR 0007 esistono, ciascuno su una sola riga (regola di presenza verde)
- AC-10 Il driver apre il DB con journal WAL, foreign_keys=ON e secure_delete=ON (pragma letti nel test)
- AC-11 UnitaDiLavoroSql passa UnitaDiLavoroContratto: rollback su Errore e su eccezione
- AC-12 Un DB con versione di schema più recente dell'app viene rifiutato con un errore chiaro, senza modificarlo
- AC-13 attribuzione ha chiave (registrazione_id, voce_id) e impronta_vocale rifiuta una seconda riga con stessi (parlante_id, registrazione_id, voce_id)

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

Sources: ADRs 0002, 0003, 0006, 0007, 0009, 0012 (.mismagent/decisions/); ADR 0006/0007/0009/0012, dev-architecture-app.md #repository, features/trascrizione-con-parlanti/tactical-model.md (all aggregates).
