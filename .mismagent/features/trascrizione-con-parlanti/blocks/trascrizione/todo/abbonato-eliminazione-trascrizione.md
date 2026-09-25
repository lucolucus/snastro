---
id: "abbonato-eliminazione-trascrizione"
type: "adapter"
context: "trascrizione"
side: "app"
wave: 5
release: "R2"
module: ":trascrizione:adattatori (..eventi)"
consumes:
  - "kernel-pl"
  - "eventi-progetto"
depends_on:
  - "eliminazione-registrazione-policy"
related_adrs:
  - "0002"
  - "0003"
  - "0012"
  - "0014"
  - "0018"
  - "0020"
---
# abbonato-eliminazione-trascrizione — Abbonato sincrono a RegistrazioneEliminata → eliminazione-registrazione-policy

## What to do
AbbonatoEliminazioneRegistrazione(dispatcher, politica): registers ONE synchronous subscriber in init that maps RegistrazioneEliminata(r, …) → politica.applica(r) inside the publishing transaction (its Errore dooms it) and ignores every other event. No Parlanti query (ADR 0018 prohibition).

Note: NEW 2026-09-25 (ADR 0020 §2 step 4, manifest delta 2026-09-25-elimina-registrazione; wave 5 = after its policy, the delta's 'wave 19' corrected at fold): the Trascrizione synchronous subscriber of RegistrazioneEliminata; registered by avvio-parlanti before the first command (AC-630). R0/R1 compositions do not register it and do not offer the action.

## Tasks
- AC-612 AbbonatoEliminazioneRegistrazione(dispatcher, politica) registers ONE synchronous subscriber in init: RegistrazioneEliminata(r, …) → politica.applica(r) inside the publishing transaction, and its Errore dooms the transaction (test on DispatcherEventiInMemoria with a fake UoW); every other event → Ok, with no call. The adapter never uses Parlanti queries (ADR 0018 prohibition)

## Dependencies
- Blocks built first: `eliminazione-registrazione-policy` (wave 4)
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
- **eventi-progetto** (consumed/implemented) — owner `eventi-pubblicati`, supplier `crea-progetto, servizi-registrazione, RinominaRegistrazione (progetto:applicazione, fix-batch-11), elimina-registrazione (RegistrazioneEliminata, ADR 0020)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoCreato`: data class(progettoId: ProgettoId, nome: String) : EventoPubblicato
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — AFTER-COMMIT consumers only (view refresh); NO synchronous subscriber (no automatic start on import, ADR 0014 / ADR 0012 Amendment (c))
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
    - `RegistrazioneRinominata`: data class(registrazioneId: RegistrazioneId, precedente: String, nuovo: String) : EventoPubblicato — precedente/nuovo = the titolo before/after RinominaRegistrazione (fix-batch-11, AC-360/361 in tasks/app/done/r0-feedback-1.md); AFTER-COMMIT consumers: AggiornamentiVista (avvio-r0, AC-366) and abbonato-documento (AC-186bis)
    - `RegistrazioneEliminata`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) : EventoPubblicato — published by EliminaRegistrazione INSIDE its transaction, BEFORE the registrazione row is removed; titolo/data/riferimento are the values at deletion (the only way after-commit consumers can locate the files). SYNCHRONOUS consumers: abbonato-eliminazione-trascrizione (veto + purge), abbonato-revisione-parlanti (Parlanti purge + INV-25); AFTER-COMMIT consumers: abbonato-documento (.md removal on the per-key queue), avvio-parlanti (AggiornamentiVistaParlanti, PuliziaRegistrazioneEliminata). ADR 0020
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: All four events → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. AMENDED 2026-09-24 (ADR 0014 / ADR 0012 Amendment (c)): the SYNCHRONOUS clause for RegistrazioneAggiunta is dropped — it has no sync subscriber (the dispatcher's sync mechanism itself is unchanged, ADR 0012). AMENDED 2026-09-24 (delta 2026-09-24-rinomina-documento): RegistrazioneRinominata pinned (already published by the merged code). EXCEPTION (ADR 0020, 2026-09-25): RegistrazioneEliminata has TWO synchronous subscribers (Trascrizione veto + purge, Parlanti purge) inside the publishing transaction; an Errore from either dooms the command and is returned unchanged by EliminaRegistrazione; its other subscribers are after commit (same at-least-once / idempotent rules; abbonato-documento serializes it on the per-registrazioneId queue behind any in-flight Rigenerazione)

Sources: ADRs 0002, 0003, 0012, 0014, 0018, 0020 (.mismagent/decisions/); ADR 0012, ADR 0020 §2, manifest delta 2026-09-25-elimina-registrazione.
