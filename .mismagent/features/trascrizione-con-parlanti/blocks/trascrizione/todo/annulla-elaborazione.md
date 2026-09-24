---
id: "annulla-elaborazione"
type: "application-service"
context: "trascrizione"
side: "app"
wave: 4
release: "R1"
module: ":trascrizione:applicazione (..comandi)"
consumes:
  - "kernel-pl"
  - "agg-elaborazione"
  - "repo-trascrizione"
  - "eventi-elaborazione"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0006"
  - "0007"
  - "0012"
  - "0014"
  - "0018"
commands:
  - "AnnullaElaborazione"
---
# annulla-elaborazione — AnnullaElaborazione (ritiro di un'Elaborazione in coda)

## What to do
AnnullaElaborazione(elaborazioneId): withdraws an Elaborazione that is still in_attesa (never started) — first transcription or re-run — by deleting its row in one transaction through the aggregate check Elaborazione.annulla() and the repository's compare-and-delete, then publishes ElaborazioneAnnullata(registrazioneId). An in_corso run cannot be cancelled; losing the race with the dispatcher gives ElaborazioneGiaAvviata.

Note: NEW 2026-09-24 (ADR 0018 Amendment 2026-09-24 (b) §3, user decision): AnnullaElaborazioneServizio(uow, elaborazioni: ElaborazioneRepository, eventi: DispatcherEventi).esegui(AnnullaElaborazione(elaborazioneId: ElaborazioneId)): Esito<Unit>, ONE transaction: trova → Elaborazione.annulla() → rimuoviInAttesa (compare-and-delete) → publish ElaborazioneAnnullata(registrazioneId). Actor: utente, S2 'Annulla' on a queued row. Only in_attesa (never started) can be cancelled; in_corso cannot (explicit). The never-started row is DELETED: INV-3 unchanged, the Registrazione returns to the state of its remaining latest Elaborazione (NON_AVVIATA / COMPLETATA with the old Trascritto / FALLITA). The race with the dispatcher is serialized by BEGIN IMMEDIATE (fix-batch-17): the loser gets ElaborazioneGiaAvviata. Never reads or writes the Trascritto; no signal to CodaElaborazioni. Release R1 (first transcriptions are R1).

## Tasks
- AC-464 First transcription cancelled: with a single in_attesa for r, AnnullaElaborazione(id) → Ok; diRegistrazione(r) is empty; exactly one ElaborazioneAnnullata(r) is published (recording dispatcher fake), inside the transaction (one inTransazione)
- AC-465 Queued re-run cancelled: with completata + in_attesa for r → Ok; only the completata is left, unchanged (same state and numeroPersone); TrascrittoRepository is never called (counting fake: no trova, no salva). A queued 'Riprova' (fallita + in_attesa) → Ok, the fallita stays
- AC-466 Rejections, nothing written and nothing published: the id is in_corso, completata or fallita → Errore(ElaborazioneGiaAvviata(id)); unknown id → Errore(ElaborazioneNonTrovata(id))
- AC-467 Race with the dispatcher: the repository fake answers rimuoviInAttesa with Errore(ElaborazioneGiaAvviata) (the claim won between trova and the delete) → the service returns that error unchanged, publishes nothing, and the transaction rolls back
- AC-468 After a cancellation, AvviaElaborazione(r, n) is accepted again (INV-4: none open) and creates a new in_attesa

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
- **agg-elaborazione** (consumed/implemented) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Elaborazione.accoda`: (id: ElaborazioneId, registrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Creato<Elaborazione, ElaborazioneAccodata> — numeroPersone fixed at creation (may be absent), immutable (ADR 0014)
    - `Elaborazione.numeroPersone`: NumeroPersone? — read-only accessor; set only by accoda (and by the persistence reconstitution); no transition changes it
    - `NumeroPersone`: @JvmInline value class(valore: Int) in :trascrizione:dominio — 1..10 inclusive; factory NumeroPersone.di(n: Int): Esito<NumeroPersone> → Errore(NumeroPersoneFuoriIntervallo) outside 1..10 (sealed ErroreTrascrizione, ErroriTrascrizione.kt); the only way to build one (ADR 0014)
    - `Elaborazione.avvia`: (alle: Instant): Esito<ElaborazioneAvviata>
    - `Elaborazione.completa`: (): Esito<ElaborazioneCompletata>
    - `Elaborazione.fallisci`: (motivo: String): Esito<ElaborazioneFallita>
    - `Elaborazione.annulla`: (): Esito<ElaborazioneAnnullata> — Ok ONLY from in_attesa (domain event ElaborazioneAnnullata(id, registrazioneId), state unchanged: a check, not a transition — the repository then deletes the never-started row, ADR 0018 Amendment (b)); any other state → Errore(ElaborazioneGiaAvviata(id))
    - `named predicates`: aperta (in_attesa|in_corso), inAttesa, completata, fallita, terminale — never compare StatoElaborazione outside the aggregate
    - `errors (ErroreTrascrizione, ErroriTrascrizione.kt)`: ElaborazioneGiaAperta(registrazioneId); ElaborazioneGiaAvviata(elaborazioneId: ElaborazioneId); ElaborazioneNonTrovata(elaborazioneId: ElaborazioneId); NumeroPersoneFuoriIntervallo(valore) — ElaborazioneGiaCompletata is DELETED (ADR 0018)
  - keys (minting rules):
    - `ElaborazioneId`: minted by avvia-elaborazione via GeneratoreId (UUID v4) — internal, never crosses a context boundary
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(elaborazioneQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoElaborazione\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./trascrizione/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; trova(id: ElaborazioneId): Elaborazione?; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta) only: another open Elaborazione of the same Registrazione while this one is open (index elaborazione_aperta_unica); several completata are allowed (ADR 0018) */; rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> /* compare-and-delete (ADR 0018 Amendment (b)): deletes the row iff it exists and is still in_attesa; started → Errore(ElaborazioneGiaAvviata); absent → Errore(ElaborazioneNonTrovata); the only deletion of an Elaborazione */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento; salva over an existing Trascritto REPLACES it whole (Voci, Segmenti, counters: ADR 0018 replacement)
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

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012, 0014, 0018 (.mismagent/decisions/); ADR 0018 Amendment 2026-09-24 (b) §3, features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (Amendment 2026-09-24 (b)), ADR 0004/0007/0012.
