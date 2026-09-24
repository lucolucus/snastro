---
id: "persistenza-ritrascrivi"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 3
release: "R2"
module: ":persistenza"
consumes:
  - "kernel-pl"
depends_on:
  - "persistenza-schema"
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0007"
  - "0012"
  - "0018"
tables:
  - "elaborazione"
---
# persistenza-ritrascrivi — Migrazione 3.sqm (Ritrascrivi) + eliminaInAttesa

## What to do
Forward-only migration migrations/3.sqm (schema 3 → 4) whose only statement drops elaborazione_completata_unica, so several completata Elaborazioni may exist (ADR 0018); plus the eliminaInAttesa query (compare-and-delete of a never-started row) used by AnnullaElaborazione. No other table or index changes.

Note: NEW 2026-09-24 (ADR 0018, manifest delta 2026-09-24-ritrascrivi; wave 3 = right after persistenza-schema, the delta's 'wave 1' corrected at fold): owns migrations/3.sqm (R0/R1 are released, so forward-only, never an edit of 1.sqm/2.sqm, ADR 0006 (a)) and the eliminaInAttesa query used by repository-sql-trascrizione (AnnullaElaborazione, ADR 0018 Amendment (b) §3). ADR 0018 enforced_by (presence clause) is exigible_from this block. Release R2 for 3.sqm; eliminaInAttesa is needed by R1's AnnullaElaborazione — this block is therefore built before repository-sql-trascrizione's rework whatever the release.

## Tasks
- AC-425 migrations/3.sqm (schema 3 → 4, forward-only) contains exactly one statement, `DROP INDEX elaborazione_completata_unica;`, on one line; no other table or index changes, and SnastroDatabase.Schema.version = 4. The CR-13 migration test of :persistenza:test stays green: an empty DB migrates to the current version, passes integrity checks and runs every query; Schema.migrate from empty equals Schema.create
- AC-426 Migration fixture test: a DB frozen at version 3, holding one completata Elaborazione, its Trascritto (Voci/Segmenti), an attribuzione and an impronta_vocale row, is migrated to 4; every row is intact and user_version = 4; sqlite_master still has elaborazione_aperta_unica and parlante_nome_attivo_unico, and has no elaborazione_completata_unica
- AC-427 After the migration: two completata rows for the same registrazione_id insert fine; two rows in in_attesa | in_corso for the same registrazione_id are still refused by the index; a completata next to an in_attesa for the same Registrazione is accepted
- AC-471 Elaborazione.sq gains `eliminaInAttesa: DELETE FROM elaborazione WHERE id = :id AND stato = 'in_attesa';` (the only DELETE on elaborazione); SQL test: on an in_corso row it affects 0 rows and the row is intact; on an in_attesa row it affects 1

## Dependencies
- Blocks built first: `persistenza-schema` (wave 2)
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

Sources: ADRs 0002, 0003, 0006, 0007, 0012, 0018 (.mismagent/decisions/); ADR 0006/0007/0018 (+ Amendment 2026-09-24 (b)), features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (INV-4), manifest delta 2026-09-24-ritrascrivi.
