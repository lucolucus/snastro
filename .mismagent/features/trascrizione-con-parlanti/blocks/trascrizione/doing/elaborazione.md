---
id: "elaborazione"
type: "aggregate"
context: "trascrizione"
side: "app"
wave: 2
release: "R1"
module: ":trascrizione:dominio"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0007"
  - "0012"
  - "0014"
invariants:
  - "INV-3 StatoElaborazione moves only in_attesa → in_corso → completata | fallita; completata and fallita are terminal"
invariant_fields:
  - "stato"
  - "motivoFallimento"
  - "avviataAlle"
identity: "ElaborazioneId — UUID from GeneratoreId"
tables:
  - "elaborazione"
owns_boundaries:
  agg-elaborazione:
    projection: "in-process"
    contract_test: "invariant-test"
    pinned_types:
      "Elaborazione.accoda": "(id: ElaborazioneId, registrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Creato<Elaborazione, ElaborazioneAccodata> — numeroPersone fixed at creation (may be absent), immutable (ADR 0014)"
      "Elaborazione.numeroPersone": "NumeroPersone? — read-only accessor; set only by accoda (and by the persistence reconstitution); no transition changes it"
      NumeroPersone: "@JvmInline value class(valore: Int) in :trascrizione:dominio — 1..10 inclusive; factory NumeroPersone.di(n: Int): Esito<NumeroPersone> → Errore(NumeroPersoneFuoriIntervallo) outside 1..10 (sealed ErroreTrascrizione, ErroriTrascrizione.kt); the only way to build one (ADR 0014)"
      "Elaborazione.avvia": "(alle: Instant): Esito<ElaborazioneAvviata>"
      "Elaborazione.completa": "(): Esito<ElaborazioneCompletata>"
      "Elaborazione.fallisci": "(motivo: String): Esito<ElaborazioneFallita>"
      "named predicates": "aperta (in_attesa|in_corso), completata, fallita, terminale — never compare StatoElaborazione outside the aggregate"
---
# elaborazione — Aggregato Elaborazione

## What to do
Elaborazione root: accoda (in_attesa, creataAlle), avvia(alle) → in_corso, completa, fallisci(motivo); named predicates aperta/completata/fallita/terminale. Built BEFORE trascritto (shared ErroriTrascrizione.kt, R20).

REWORK 2026-09-24 (ADR 0014): add the VO NumeroPersone (@JvmInline value class(valore: Int), NumeroPersone.di(n) → Esito, 1..10, else Errore(NumeroPersoneFuoriIntervallo) added to ErroriTrascrizione.kt); Elaborazione gains the immutable optional field numeroPersone (read-only accessor), taken by accoda(..., numeroPersone) and by the persistence reconstitution; transitions never touch it. New tests AC-367, AC-368.

Note: AMENDED 2026-09-24 (ADR 0014, user 2026-09-23): the VO NumeroPersone (1..10, NumeroPersoneFuoriIntervallo in ErroriTrascrizione.kt) and the immutable optional field numeroPersone live here; accoda takes it, the reconstitution restores it.

### Invariants owned here (one test each, name starts with the tag)
- INV-3 StatoElaborazione moves only in_attesa → in_corso → completata | fallita; completata and fallita are terminal

## Tasks
- INV-3 da in_attesa si passa solo a in_corso; da in_corso a completata o fallita (ogni transizione ammessa restituisce il suo evento)
- INV-3 ogni transizione da completata o fallita, e ogni salto (in_attesa → completata), restituisce Errore(TransizioneNonAmmessa) e lo stato non cambia
- AC-19 fallisci conserva il motivo; avvia registra avviataAlle
- AC-367 (ex AC-NP1) Elaborazione.accoda fissa numeroPersone (anche assente) alla creazione; è immutabile e di sola lettura: nessuna transizione (avvia, completa, fallisci) lo cambia
- AC-368 (ex AC-NP2) NumeroPersone.di: 1 e 10 → Ok; 0, -1 e 11 → Errore(NumeroPersoneFuoriIntervallo) (test a tabella)

## Dependencies
- **agg-elaborazione** (OWNED here — built before its consumers) — owner `elaborazione`, projection in-process, contract_test **invariant-test**
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

Sources: ADRs 0002, 0003, 0004, 0007, 0012, 0014 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Trascrizione (+ Amendment 2026-09-23 (c)), ADR 0014.
