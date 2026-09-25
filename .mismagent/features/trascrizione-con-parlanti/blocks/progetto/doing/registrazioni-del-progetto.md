---
id: "registrazioni-del-progetto"
type: "read-model"
context: "progetto"
side: "app"
wave: 5
release: "R0"
module: ":progetto:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "agg-registrazione"
  - "repo-progetto"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0006"
  - "0010"
  - "0012"
  - "0020"
view_shape:
  registrazioni: "List<{registrazioneId, titolo, dataRegistrazione, durataMs}>"
view_sources:
  registrazioni: "← Registrazione aggregate via RegistrazioneRepository.delProgetto"
---
# registrazioni-del-progetto — RegistrazioniDelProgetto — fetta Progetto (S2)

## What to do
Progetto slice of S2 (R1 split): ordered by dataRegistrazione desc, tie aggiuntaAlle desc.

### view_shape (field ← source)
- `registrazioni`: List<{registrazioneId, titolo, dataRegistrazione, durataMs}> ← ← Registrazione aggregate via RegistrazioneRepository.delProgetto

## Tasks
- AC-161 La vista espone registrazioneId, titolo, dataRegistrazione e durataMs, ordinati per data dalla più recente (a parità, l'ultima aggiunta prima)

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

Sources: ADRs 0002, 0003, 0006, 0010, 0012, 0020 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1).
