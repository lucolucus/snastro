---
id: "persistenza-conferma-segmento"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 4
release: "R2"
module: ":persistenza"
consumes:
  - "kernel-pl"
depends_on:
  - "persistenza-ritrascrivi"
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0012"
  - "0019"
tables:
  - "segmento"
---
# persistenza-conferma-segmento — Migrazione 4.sqm (Segmento confermato)

## What to do
Forward-only migration migrations/4.sqm (schema 4 → 5) whose only statement adds segmento.confermato INTEGER NOT NULL DEFAULT 0 CHECK (confermato IN (0, 1)), plus the Segmento.sq queries reading and writing the column (ADR 0019 §3). Existing rows get 0. No other table or index changes.

Note: NEW 2026-09-24 (ADR 0019 §3, manifest delta 2026-09-24-semi-automatica; wave 4 = right after persistenza-ritrascrivi's 3.sqm, the delta's 'wave 3' corrected at fold): owns migrations/4.sqm (forward-only, ADR 0006 (a)) and the Segmento.sq queries that read and write the confermato column. Release R2 (only R2 actions write the flag), but the trascritto aggregate (R1 module) carries the field: this block is built BEFORE the repository-sql-trascrizione rework whatever the release (repository-sql-trascrizione depends_on it).

## Tasks
- AC-521 migrations/4.sqm (schema 4 → 5, forward-only) holds exactly one statement, `ALTER TABLE segmento ADD COLUMN confermato INTEGER NOT NULL DEFAULT 0 CHECK (confermato IN (0, 1));`, and SnastroDatabase.Schema.version = 5; the CR-13 migration test stays green (Schema.migrate from empty equals Schema.create); fixture test: a DB frozen at version 4 holding a Trascritto of 3 Segmenti migrates to 5 with every row intact and confermato = 0; inserting confermato = 2 is refused

## Dependencies
- Blocks built first: `persistenza-ritrascrivi` (wave 3)
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

Sources: ADRs 0002, 0003, 0006, 0012, 0019 (.mismagent/decisions/); ADR 0006, ADR 0019 §3, manifest delta 2026-09-24-semi-automatica.
