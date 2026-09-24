---
id: "avvia-elaborazione"
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
  - "registrazione-per-trascrizione"
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
  - "AvviaElaborazione"
invariants:
  - "INV-4 (REWRITTEN 2026-09-24, ADR 0018) per Registrazione at most one Elaborazione in_attesa|in_corso; a new one iff none is open (after none, fallita, or completata = Ritrascrivi); several completata allowed (history)"
---
# avvia-elaborazione — AvviaElaborazione (accodamento, riprova e ritrascrizione)

## What to do
Enqueue an Elaborazione in_attesa (actor: the utente only — 'Trascrivi' on a NON_AVVIATA row and 'Riprova' after fallita; ~~the RegistrazioneAggiunta sync subscriber~~ is removed, ADR 0014). AvviaElaborazione(registrazioneId, numeroPersone: Int? = null): numeroPersone is validated via NumeroPersone.di before any write and passed to Elaborazione.accoda. INV-4 pre-check + index backstop (ADR 0007).

REWORK 2026-09-24 (ADR 0014): the command gains numeroPersone: Int? = null (validated with NumeroPersone.di → NumeroPersoneFuoriIntervallo, no row on error) and passes it to Elaborazione.accoda; a retry after fallita stores the submitted value. Remove any KDoc/comment naming the RegistrazioneAggiunta subscriber as an actor. New test AC-369.

REWORK 2026-09-24 (ADR 0018): drop the 'any completata → ElaborazioneGiaCompletata' pre-check and its test (INV-4 rewritten: a new run iff none is open); 'Ritrascrivi' is the same command and never touches the Trascritto. New tests AC-434..AC-436.

Note: AMENDED 2026-09-24 (ADR 0014, ADR 0012 Amendment (c)): signature AvviaElaborazione(registrazioneId, numeroPersone: Int? = null); the ONLY actor is the utente ('Trascrivi' on NON_AVVIATA, 'Riprova' on fallita) — no subscriber calls it (Q-6 superseded); numeroPersone is validated via NumeroPersone.di BEFORE any write. AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): the only pre-check is 'any open → ElaborazioneGiaAperta'; actors add 'Ritrascrivi' on a completata row (prefilled with the latest numeroPersone). AvviaElaborazione never touches the Trascritto.

### Invariants owned here (one test each, name starts with the tag)
- INV-4 (REWRITTEN 2026-09-24, ADR 0018) per Registrazione at most one Elaborazione in_attesa|in_corso; a new one iff none is open (after none, fallita, or completata = Ritrascrivi); several completata allowed (history)

## Tasks
- AC-64 AvviaElaborazione su una Registrazione senza Elaborazioni crea un'Elaborazione in_attesa
- INV-4 con un'Elaborazione in_attesa o in_corso già presente → ElaborazioneGiaAperta e nessuna riga nuova
- AC-65 Dopo una fallita → nuova Elaborazione in_attesa; la fallita resta nello storico
- AC-66 Se il repository segnala la violazione dell'indice, il servizio restituisce lo stesso ErroreDominio (mai un'eccezione grezza)
- AC-67 Registrazione inesistente → RegistrazioneNonTrovata
- AC-369 (ex AC-NP3) AvviaElaborazione con numeroPersone assente → Elaborazione in_attesa senza numero; con 4 → in_attesa con numeroPersone 4; con 0 o 11 → Errore(NumeroPersoneFuoriIntervallo) e nessuna riga nuova; la riprova dopo una fallita salva il valore inviato (la fallita resta invariata)
- AC-434 (Ritrascrivi) After a completata, AvviaElaborazione(r, 3) creates a new in_attesa with numeroPersone 3; the completata row is unchanged (same state, same numeroPersone), and so is the Trascritto: same Voci, Segmenti and counters, and no TrascrittoRepository.salva call (counting fake)
- AC-435 With completata + in_attesa (a Ritrascrizione queued), or with completata + in_corso, another AvviaElaborazione → Errore(ElaborazioneGiaAperta), and no new row
- AC-436 History: completata, then a fallita re-run, then AvviaElaborazione → Ok (3 rows, the first two unchanged); completata, completata, then AvviaElaborazione → Ok; NumeroPersoneFuoriIntervallo still wins before any write (AC-369)

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
- **registrazione-per-trascrizione** (consumed/implemented) — owner `porta-registrazione-trascrizione`, supplier `catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreRegistrazione`: interface { fun registrazione(id: RegistrazioneId): RegistrazioneVista? } — null if unknown
    - `RegistrazioneVista`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, riferimentoAudio: RiferimentoAudio, dataRegistrazione: LocalDate, durataMs: Long)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
    - `titolo`: minted by servizi-registrazione: source file name without extension; immutable

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012, 0014, 0018 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (INV-4, Q-6 superseded by Amendment 2026-09-23 (c)), ADR 0007, ADR 0014.
