---
id: "kernel"
type: "port"
context: "piattaforma"
side: "app"
wave: 1
module: ":kernel"
consumes: []
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0012"
owns_boundaries:
  kernel-pl:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ProgettoId: "@JvmInline value class(valore: String) — UUID"
      RegistrazioneId: "@JvmInline value class(valore: String) — UUID"
      ElaborazioneId: "@JvmInline value class(valore: String) — UUID"
      ParlanteId: "@JvmInline value class(valore: String) — UUID"
      VoceId: "@JvmInline value class(numero: Int) — equals the n of 'Voce n'"
      SegmentoId: "@JvmInline value class(numero: Int)"
      VoceRef: "data class(registrazioneId: RegistrazioneId, voceId: VoceId)"
      IntervalloMs: "data class(inizioMs: Long, fineMs: Long) — require 0 <= inizioMs < fineMs; ordered numerically"
      RiferimentoAudio: "@JvmInline value class(percorsoRelativo: String)"
      CampioniAudio: "class(campioni: FloatArray) — 16 kHz mono float, explicit equals/hashCode (CR-5)"
      EstrattoRef: "data class(registrazioneId: RegistrazioneId, intervalli: List<IntervalloMs>) — non-empty, ordered by inizioMs, total duration <= 10 000 ms, played as a sequence"
      Esito: "sealed interface Esito<out T> { Ok<T>(valore: T); Errore(errore: ErroreDominio) } + poi / mappa / seErrore"
      ErroreDominio: "interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errore<Contesto> : ErroreDominio in file Errori<Contesto>.kt (ADR 0003 amended)"
      EventoDominio: "marker interface for domain events (returned by aggregate methods)"
      EventoPubblicato: "marker interface for published events (Published Language, <ctx>:applicazione.eventi)"
      Creato: "data class Creato<A, E>(aggregato: A, evento: E)"
      GeneratoreId: "interface { fun nuovo(): String } — UUID v4; testFixtures GeneratoreIdFinto: 'id-1', 'id-2', …"
      UnitaDiLavoro: "interface { fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — rolls back when the block returns Errore or throws"
      DispatcherEventi: "interface { fun pubblica(evento: EventoPubblicato) } + registration of AbbonatoSincrono / AbbonatoDopoCommit"
      RicostituzioneDaPersistenza: "@RequiresOptIn(level = ERROR) annotation class — only ..adattatori.persistenza.. opts in (CR-15)"
---
# kernel — Shared kernel: ids, VO del Published Language, Esito, porte di kernel

## What to do
Implement the shared kernel pinned by boundary kernel-pl: aggregate ids (UUID value classes), VoceId/SegmentoId Int value classes, VoceRef, IntervalloMs, RiferimentoAudio, CampioniAudio, EstrattoRef, Esito API, ErroreDominio (plain interface), EventoDominio/EventoPubblicato markers, Creato, RicostituzioneDaPersistenza, the ports GeneratoreId/UnitaDiLavoro/DispatcherEventi with their Finta + Contratto in testFixtures, the in-memory DispatcherEventi implementation (sync subscribers in-transaction, after-commit queue) and the test helpers atteso()/erroreAtteso().

## Tasks
- AC-1 Esito.poi propaga il primo Errore senza eseguire i passi successivi; mappa trasforma solo Ok
- AC-2 UnitaDiLavoroContratto passa contro UnitaDiLavoroFinta: un blocco che restituisce Errore o lancia non lascia effetti, Ok li conserva
- AC-3 DispatcherEventi: gli abbonati sincroni girano nell'ordine di pubblicazione dentro la transazione e un loro Errore annulla il comando; gli abbonati dopo-commit girano solo dopo il commit e mai dopo un rollback
- AC-4 GeneratoreIdFinto produce 'id-1', 'id-2', … in sequenza
- AC-5 IntervalloMs rifiuta inizio >= fine o inizio < 0 (require — errore di programmazione)
- AC-6 CampioniAudio ed EstrattoRef sono uguali per valore (stesso contenuto → equals e hashCode uguali)
- AC-7 Nessun sottotipo di ErroreDominio estende Throwable (Konsist CR-8)

## Dependencies
- **kernel-pl** (OWNED here — built before its consumers) — owner `kernel`, projection in-process, contract_test **consumer-driven**
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

Sources: ADRs 0002, 0003, 0012 (.mismagent/decisions/); architecture.md (kernel row), dev-architecture-app.md #valori-id #servizio #test, features/trascrizione-con-parlanti/architetture/architecture-overview.md (Published Language).
