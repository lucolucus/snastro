---
id: "elimina-registrazione"
type: "application-service"
context: "progetto"
side: "app"
wave: 4
release: "R2"
module: ":progetto:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-registrazione"
  - "repo-progetto"
  - "eventi-progetto"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0010"
  - "0012"
  - "0014"
  - "0020"
model_hint: "deep"
commands:
  - "EliminaRegistrazione"
invariants:
  - "INV-28 a Registrazione can be eliminata only if none of its Elaborazioni is in_attesa | in_corso; its elimination removes, in ONE transaction, the Registrazione, every Elaborazione of it, its Trascritto (Voci, Segmenti) and every Attribuzione and ImprontaVocale keyed by one of its VoceRefs (then INV-25); after the COMMIT no row keyed by its registrazioneId survives except its eliminazione_in_sospeso row, removed once its files are gone; a refused or failed elimination changes nothing"
---
# elimina-registrazione — EliminaRegistrazione (eliminazione definitiva di una Registrazione, una transazione)

## What to do
EliminaRegistrazioneServizio(uow, registrazioni, inSospeso, eventi).esegui(EliminaRegistrazione(registrazioneId)): ONE transaction — trova → Registrazione.elimina() → inSospeso.registra(EliminazioneInSospeso) → eventi.pubblica(RegistrazioneEliminata) (the Trascrizione veto + purge and the Parlanti purge run here, synchronously) → registrazioni.rimuovi(id) → COMMIT. A synchronous subscriber's Errore (ElaborazioneGiaAperta) is returned unchanged and nothing changes. Thin: no file I/O, no implicit cancellation, no Trascrizione/Parlanti query.

Note: NEW 2026-09-25 (ADR 0020 §1-§2, manifest delta 2026-09-25-elimina-registrazione; wave 4 = first wave after its owners registrazione/porte-progetto/eventi-pubblicati and their ADR 0020 reworks, the delta's 'wave 18' corrected at fold): EliminaRegistrazioneServizio(uow, registrazioni: RegistrazioneRepository, inSospeso: EliminazioniInSospeso, eventi: DispatcherEventi).esegui(c: EliminaRegistrazione(registrazioneId)): Esito<Unit>. Thin: trova → Registrazione.elimina() (pure, returns the event) → inSospeso.registra → eventi.pubblica (the two SYNCHRONOUS subscribers run here: Trascrizione veto + purge, Parlanti purge + INV-25) → registrazioni.rimuovi (AFTER pubblica: the elaborazione/trascritto FKs are immediate) → COMMIT. It never cancels an in_attesa Elaborazione implicitly [user default]; the veto error is returned unchanged. Release R2: offered only by avvio-parlanti, which registers both synchronous subscribers (without them the FKs fail the command — safe, useless). ADR 0020 prohibition: no Trascrizione/Parlanti generated query under progetto/. Discursive (code review): rimuovi after pubblica; no file I/O inside inTransazione. model_hint deep: folds 3 boundaries, carries the INV-28 ordering and the sync-subscriber error propagation.

### Invariants owned here (one test each, name starts with the tag)
- INV-28 a Registrazione can be eliminata only if none of its Elaborazioni is in_attesa | in_corso; its elimination removes, in ONE transaction, the Registrazione, every Elaborazione of it, its Trascritto (Voci, Segmenti) and every Attribuzione and ImprontaVocale keyed by one of its VoceRefs (then INV-25); after the COMMIT no row keyed by its registrazioneId survives except its eliminazione_in_sospeso row, removed once its files are gone; a refused or failed elimination changes nothing

## Tasks
- AC-600 (INV-28) Happy path, all inside ONE inTransazione (the fake UoW records exactly one), in this order checked by a recording fake: (1) trova; (2) inSospeso.registra(EliminazioneInSospeso(id, titolo, data, riferimento)) with the values of the stored Registrazione; (3) pubblica(RegistrazioneEliminata(id, progettoId, titolo, data, riferimento)) exactly once; (4) registrazioni.rimuovi(id). Result Ok, and trova(id) is then null
- AC-601 An unknown id → Errore(ErroreProgetto.RegistrazioneNonTrovata(id)); nothing is registered, published or removed
- AC-602 (INV-28) A synchronous subscriber that answers RegistrazioneEliminata with Errore(ErroreTrascrizione.ElaborazioneGiaAperta(id)) makes esegui return that same error (DispatcherEventiInMemoria with the finte); afterwards the Registrazione still exists, no eliminazione_in_sospeso row exists, and no after-commit subscriber received anything
- AC-603 The service touches no file: ArchivioAudio is not among its collaborators (constructor test / Konsist); after-commit work belongs to subscribers
- AC-604 Two deletions of the same id: the first → Ok, the second → RegistrazioneNonTrovata, and nothing is published twice

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
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
- **agg-registrazione** (consumed/implemented) — owner `registrazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Registrazione.aggiungi`: (id, progettoId, titolo: String, riferimentoAudio, durataMs: Long, dataRegistrazione: LocalDate, aggiuntaAlle: Instant): Creato<Registrazione, RegistrazioneAggiunta>
    - `Registrazione.modificaData`: (nuova: LocalDate): Esito<DataRegistrazioneModificata>
    - `Registrazione.elimina`: (): RegistrazioneEliminata — pure: returns the domain event (id, progettoId, titolo, dataRegistrazione, riferimentoAudio); no state change, no guard (the only precondition, INV-28's 'no open Elaborazione', is Trascrizione's and is checked by its synchronous subscriber) — ADR 0020
    - `invariant_fields exposure`: progettoId (val, immutable), dataRegistrazione (private set)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(registrazioneQueries)\b' . | grep -vE '^\./(persistenza/|progetto/adattatori/src/[A-Za-z]+/kotlin/snastro/progetto/adattatori/persistenza/)' | grep -q .`
- **repo-progetto** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoRepository`: interface { trova(): Progetto?; salva(p: Progetto) } — one Progetto per project DB
    - `RegistrazioneRepository`: interface { trova(id: RegistrazioneId): Registrazione?; delProgetto(id: ProgettoId): List<Registrazione>; titoliDelProgetto(id: ProgettoId): List<String> /* titles only, no order, for the titolo uniqueness of AggiungiRegistrazione (AC-322) */; salva(r: Registrazione); rimuovi(id: RegistrazioneId) /* deletes the registrazione row inside the caller's transaction; absent id = no-op; the ONLY physical deletion of a Registrazione (ADR 0020) */ }
    - `EliminazioniInSospeso`: interface { registra(e: EliminazioneInSospeso); elenco(): List<EliminazioneInSospeso> /* by eliminataAlle, then id */; concludi(id: RegistrazioneId) /* absent = no-op */ } — Progetto-owned table eliminazione_in_sospeso (5.sqm, ADR 0020 §4); no biometric data
    - `EliminazioneInSospeso`: data class(registrazioneId: RegistrazioneId, titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) — the values of the Registrazione AT deletion (the only way to locate its files afterwards)
  - keys (minting rules):
    - `EliminazioneInSospeso.registrazioneId`: the deleted Registrazione's id (minted by servizi-registrazione, see kernel-pl keys); PRIMARY KEY of eliminazione_in_sospeso — at most one pending row per id; written in the deleting transaction, so it exists iff the deletion committed; removed by concludi
    - `eliminataAlle`: NOT part of the pinned type: minted by repository-sql-progetto (EliminazioniInSospesoSql.registra) from an injected java.time.Clock, epoch millis, ordering only (elenco by eliminataAlle, then registrazioneId); the Finta orders by insertion
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

Sources: ADRs 0002, 0003, 0006, 0010, 0012, 0014, 0020 (.mismagent/decisions/); ADR 0003/0012, ADR 0020 §1/§2/§7, features/trascrizione-con-parlanti/tactical-model.md § Amendment 2026-09-25 (ADR 0020), manifest delta 2026-09-25-elimina-registrazione.
