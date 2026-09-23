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
commands:
  - "AvviaElaborazione"
invariants:
  - "INV-4 per Registrazione at most one Elaborazione in_attesa|in_corso and at most one completata; a new one only if every previous one is fallita"
---
# avvia-elaborazione — AvviaElaborazione (accodamento e riprova)

## What to do
Enqueue an Elaborazione in_attesa (actor: the utente only — 'Trascrivi' on a NON_AVVIATA row and 'Riprova' after fallita; ~~the RegistrazioneAggiunta sync subscriber~~ is removed, ADR 0014). AvviaElaborazione(registrazioneId, numeroPersone: Int? = null): numeroPersone is validated via NumeroPersone.di before any write and passed to Elaborazione.accoda. INV-4 pre-check + index backstop (ADR 0007).

REWORK 2026-09-24 (ADR 0014): the command gains numeroPersone: Int? = null (validated with NumeroPersone.di → NumeroPersoneFuoriIntervallo, no row on error) and passes it to Elaborazione.accoda; a retry after fallita stores the submitted value. Remove any KDoc/comment naming the RegistrazioneAggiunta subscriber as an actor. New test AC-369.

Note: AMENDED 2026-09-24 (ADR 0014, ADR 0012 Amendment (c)): signature AvviaElaborazione(registrazioneId, numeroPersone: Int? = null); the ONLY actor is the utente ('Trascrivi' on NON_AVVIATA, 'Riprova' on fallita) — no subscriber calls it (Q-6 superseded); numeroPersone is validated via NumeroPersone.di BEFORE any write.

### Invariants owned here (one test each, name starts with the tag)
- INV-4 per Registrazione at most one Elaborazione in_attesa|in_corso and at most one completata; a new one only if every previous one is fallita

## Tasks
- AC-64 AvviaElaborazione su una Registrazione senza Elaborazioni crea un'Elaborazione in_attesa
- INV-4 con un'Elaborazione in_attesa o in_corso già presente → ElaborazioneGiaAperta e nessuna riga nuova
- INV-4 dopo una completata → ElaborazioneGiaCompletata (nessuna ri-elaborazione)
- AC-65 Dopo una fallita → nuova Elaborazione in_attesa; la fallita resta nello storico
- AC-66 Se il repository segnala la violazione dell'indice, il servizio restituisce lo stesso ErroreDominio (mai un'eccezione grezza)
- AC-67 Registrazione inesistente → RegistrazioneNonTrovata
- AC-369 (ex AC-NP3) AvviaElaborazione con numeroPersone assente → Elaborazione in_attesa senza numero; con 4 → in_attesa con numeroPersone 4; con 0 o 11 → Errore(NumeroPersoneFuoriIntervallo) e nessuna riga nuova; la riprova dopo una fallita salva il valore inviato (la fallita resta invariata)

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
- **agg-elaborazione** (consumed/implemented) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
  - pinned types:
    - `Elaborazione.accoda`: (id: ElaborazioneId, registrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Creato<Elaborazione, ElaborazioneAccodata> — numeroPersone fixed at creation (may be absent), immutable (ADR 0014)
    - `Elaborazione.numeroPersone`: NumeroPersone? — read-only accessor; set only by accoda (and by the persistence reconstitution); no transition changes it
    - `NumeroPersone`: @JvmInline value class(valore: Int) in :trascrizione:dominio — 1..10 inclusive; factory NumeroPersone.di(n: Int): Esito<NumeroPersone> → Errore(NumeroPersoneFuoriIntervallo) outside 1..10 (sealed ErroreTrascrizione, ErroriTrascrizione.kt); the only way to build one (ADR 0014)
    - `Elaborazione.avvia`: (alle: Instant): Esito<ElaborazioneAvviata>
    - `Elaborazione.completa`: (): Esito<ElaborazioneCompletata>
    - `Elaborazione.fallisci`: (motivo: String): Esito<ElaborazioneFallita>
    - `named predicates`: aperta (in_attesa|in_corso), completata, fallita, terminale — never compare StatoElaborazione outside the aggregate
  - keys (minting rules):
    - `ElaborazioneId`: minted by avvia-elaborazione via GeneratoreId (UUID v4) — internal, never crosses a context boundary
  - §14 gates (must stay green):
    - `! grep -rnE --include='*.kt' --exclude-dir=build '\b(elaborazioneQueries)\b' . | grep -vE '^\./(persistenza/|trascrizione/adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
    - `! grep -rnE --include='*.kt' --exclude-dir=build 'StatoElaborazione\.' . | grep -E '^\./[^:]*/src/main/' | grep -vE '^\./trascrizione/(dominio/|adattatori/src/[A-Za-z]+/kotlin/snastro/trascrizione/adattatori/persistenza/)' | grep -q .`
- **repo-trascrizione** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta | ElaborazioneGiaCompletata) from the ADR 0007 indexes */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento
- **registrazione-per-trascrizione** (consumed/implemented) — owner `porta-registrazione-trascrizione`, supplier `catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreRegistrazione`: interface { fun registrazione(id: RegistrazioneId): RegistrazioneVista? } — null if unknown
    - `RegistrazioneVista`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, riferimentoAudio: RiferimentoAudio, dataRegistrazione: LocalDate, durataMs: Long)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
    - `titolo`: minted by servizi-registrazione: source file name without extension; immutable

Sources: ADRs 0002, 0003, 0004, 0006, 0007, 0012, 0014 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (INV-4, Q-6 superseded by Amendment 2026-09-23 (c)), ADR 0007, ADR 0014.
