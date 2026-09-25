---
id: "persistenza-elimina-registrazione"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 5
release: "R2"
module: ":persistenza"
consumes:
  - "kernel-pl"
depends_on:
  - "persistenza-conferma-segmento"
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0009"
  - "0012"
  - "0018"
  - "0020"
tables:
  - "eliminazione_in_sospeso"
  - "registrazione"
  - "elaborazione"
  - "trascritto"
---
# persistenza-elimina-registrazione — Migrazione 5.sqm (eliminazione_in_sospeso) + query di eliminazione di una Registrazione

## What to do
Forward-only migration migrations/5.sqm (schema 5 → 6) whose only statement creates the Progetto-owned table eliminazione_in_sospeso (registrazione_id PK, titolo, data_registrazione, riferimento_audio, eliminata_alle; no FK), plus the deletion queries: Registrazione.sq elimina, EliminazioneInSospeso.sq inserisci/elenco/elimina, Elaborazione.sq eliminaDiRegistrazione, Trascritto.sq elimina. SQL-test the FK order: the registrazione row can be deleted only once its elaborazione/segmento/voce/trascritto rows are gone.

Note: NEW 2026-09-25 (ADR 0020 §4 + Consequences, manifest delta 2026-09-25-elimina-registrazione; wave 5 = right after persistenza-conferma-segmento's 4.sqm, the delta's 'wave 17' corrected at fold): owns migrations/5.sqm (forward-only, ADR 0006 (a)) and the new queries used by repository-sql-progetto (Registrazione.sq elimina, EliminazioneInSospeso.sq) and repository-sql-trascrizione (eliminaDiRegistrazione, Trascritto.sq elimina). ADR 0020 enforced_by (presence clause: 5.sqm creates eliminazione_in_sospeso on one line) is exigible_from this block. Amends ADR 0018 (b)'s 'eliminaInAttesa is the only DELETE on elaborazione' (ADR 0020 Consequences). Release R2, but built BEFORE the repository-sql-progetto / repository-sql-trascrizione reworks whatever the release (they depends_on it). eliminazione_in_sospeso holds no biometric data (ADR 0009 / ADR 0020 Privacy).

## Tasks
- AC-597 migrations/5.sqm (schema 5 → 6, forward-only) contains only `CREATE TABLE eliminazione_in_sospeso (` on one line, with columns registrazione_id TEXT NOT NULL PRIMARY KEY, titolo TEXT NOT NULL, data_registrazione TEXT NOT NULL, riferimento_audio TEXT NOT NULL, eliminata_alle INTEGER NOT NULL, and no FK; SnastroDatabase.Schema.version = 6. The CR-13 migration test stays green: an empty DB migrates, passes the integrity check and runs every query; Schema.migrate from empty equals Schema.create
- AC-598 Migration fixture test: a DB frozen at version 5, holding one Registrazione with a completata Elaborazione, a Trascritto, an attribuzione and an impronta_vocale, is migrated to 6; every row is intact, user_version = 6, and eliminazione_in_sospeso exists and is empty
- AC-599 New queries, each SQL-tested on a real SQLite DB: Registrazione.sq `elimina: DELETE FROM registrazione WHERE id = :id;`; EliminazioneInSospeso.sq `inserisci`, `elenco` (ORDER BY eliminata_alle, registrazione_id), `elimina`; Elaborazione.sq `eliminaDiRegistrazione: DELETE FROM elaborazione WHERE registrazione_id = :registrazioneId;`; Trascritto.sq `elimina: DELETE FROM trascritto WHERE registrazione_id = :registrazioneId;`. Test: deleting the registrazione row while one of its elaborazione rows exists fails immediately with an FK error; deleting it after the elaborazione, segmento, voce and trascritto rows are gone succeeds

## Dependencies
- Blocks built first: `persistenza-conferma-segmento` (wave 4)
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

Sources: ADRs 0002, 0003, 0006, 0009, 0012, 0018, 0020 (.mismagent/decisions/); ADR 0006, ADR 0020 §2/§4 + Consequences, features/trascrizione-con-parlanti/tactical-model.md § Amendment 2026-09-25 (ADR 0020), manifest delta 2026-09-25-elimina-registrazione.
