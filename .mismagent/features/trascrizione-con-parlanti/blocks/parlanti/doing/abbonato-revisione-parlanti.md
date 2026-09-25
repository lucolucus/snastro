---
id: "abbonato-revisione-parlanti"
type: "adapter"
context: "parlanti"
side: "app"
wave: 5
release: "R2"
module: ":parlanti:adattatori (..eventi)"
consumes:
  - "kernel-pl"
  - "eventi-revisione"
  - "eventi-elaborazione"
  - "eventi-progetto"
depends_on:
  - "revisione-policy"
  - "sostituzione-trascritto-policy"
related_adrs:
  - "0002"
  - "0003"
  - "0012"
  - "0014"
  - "0018"
  - "0019"
  - "0020"
---
# abbonato-revisione-parlanti — Abbonato sincrono agli eventi di Revisione, a TrascrittoSostituito e a RegistrazioneEliminata → revisione-policy / sostituzione-trascritto-policy

## What to do
AbbonatoSincrono on VociUnite / VoceDivisa / SegmentoRiassegnato invoking ApplicaRevisione in the Revisione's transaction.

REWORK 2026-09-24 (ADR 0018): + TrascrittoSostituito → ApplicaSostituzioneTrascrittoPolitica.applica(r), synchronous inside the completion transaction (its Errore rolls back); needs sostituzione-trascritto-policy. New test AC-446.

REWORK 2026-09-25 (ADR 0020): consumes eventi-progetto; + RegistrazioneEliminata(r) → ApplicaSostituzioneTrascrittoPolitica.applica(r), synchronous inside the deleting transaction (its Errore dooms it); existing mappings unchanged; the policy's KDoc names both triggers — the policy code is NOT changed (AC-621).

Note: AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): gains the translation TrascrittoSostituito → sostituzione-trascritto-policy (synchronous, in the completion transaction); it is already registered by the R2 composition before the first command (AC-457). AMENDED 2026-09-25 (ADR 0020, manifest delta 2026-09-25-elimina-registrazione, user decision 2026-09-25, defaults accepted): gains the translation RegistrazioneEliminata → sostituzione-trascritto-policy (synchronous, in the deleting transaction); consumes eventi-progetto. sostituzione-trascritto-policy itself is NOT reworked (KDoc line only, owned here).

## Tasks
- AC-142 Ciascuno dei tre eventi invoca ApplicaRevisione dentro la transazione della Revisione
- AC-143 Un Errore della policy annulla la Revisione (test end-to-end su databaseInMemoria)
- AC-446 TrascrittoSostituito(r) → ApplicaSostituzioneTrascrittoPolitica.applica(r) inside the publishing transaction; its Esito.Errore dooms and rolls back the transaction (test on DispatcherEventiInMemoria with a fake UnitaDiLavoro); ElaborazioneCompletata and every other event → no call
- AC-621 RegistrazioneEliminata(r, …) → ApplicaSostituzioneTrascrittoPolitica.applica(r) inside the publishing transaction, and its Errore dooms it; the existing mappings are unchanged; the policy's KDoc names both triggers. The policy code and its AC-428..431 are unchanged: it is reused, not reworked

## Dependencies
- Blocks built first: `revisione-policy` (wave 4), `sostituzione-trascritto-policy` (wave 4)
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
- **eventi-revisione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `revisione (VociUnite, VoceDivisa, SegmentoRiassegnato, SegmentoConfermato), riassegna-segmenti (SegmentoRiassegnato, N per batch)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
    - `SegmentoConfermato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, confermato: Boolean) : EventoPubblicato — after commit only (view refresh); no synchronous subscriber (ADR 0019 §3)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. ADR 0019: a RiassegnaSegmenti commit publishes N SegmentoRiassegnato in list order — the synchronous revisione-policy runs once per event inside the one transaction (an Errore rolls the whole batch back); after-commit subscribers are coalesced per registrazioneId as today (one Rigenerazione, one RiallineaImpronte). SegmentoConfermato → after commit only
- **eventi-elaborazione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `esegui-elaborazione (Avviata/Completata/Fallita/TrascrittoSostituito), annulla-elaborazione (ElaborazioneAnnullata)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
    - `TrascrittoSostituito`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata (ADR 0018)
    - `ElaborazioneAnnullata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by AnnullaElaborazione in the cancelling transaction (a never-started in_attesa row deleted), AFTER COMMIT only; no synchronous subscriber (ADR 0018 Amendment (b))
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. EXCEPTION (ADR 0018/0012): TrascrittoSostituito has one SYNCHRONOUS subscriber (Parlanti purge, abbonato-revisione-parlanti → sostituzione-trascritto-policy) inside the publishing transaction; its other subscribers are after commit
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

Sources: ADRs 0002, 0003, 0012, 0014, 0018, 0019, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Parlanti Policy, ADR 0012.
