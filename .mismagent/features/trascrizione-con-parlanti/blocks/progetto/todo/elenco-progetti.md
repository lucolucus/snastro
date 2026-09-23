---
id: "elenco-progetti"
type: "read-model"
context: "progetto"
side: "app"
wave: 5
module: ":progetto:applicazione (..letture)"
consumes:
  - "kernel-pl"
  - "tec-registro-progetti"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0010"
  - "0012"
view_shape:
  progetti: "List<{progettoId, nome, percorso, numRegistrazioni, ultimaAttivita}>"
view_sources:
  progetti: "← RegistroProgetti.elenco() (numRegistrazioni and ultimaAttivita cached in the registry on close, R3)"
---
# elenco-progetti — ElencoProgetti (S1)

## What to do
Query over the per-user registry, ordered by ultimaAttivita desc.

### view_shape (field ← source)
- `progetti`: List<{progettoId, nome, percorso, numRegistrazioni, ultimaAttivita}> ← ← RegistroProgetti.elenco() (numRegistrazioni and ultimaAttivita cached in the registry on close, R3)

## Tasks
- AC-159 La vista espone progettoId, nome, percorso, numRegistrazioni e ultimaAttivita per ogni progetto del registro, dal più recente
- AC-160 Registro vuoto → lista vuota

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
- **tec-registro-progetti** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RegistroProgetti`: interface { elenco(): List<VoceRegistro> /* by ultimaAttivita desc */; registra(v: VoceRegistro); aggiorna(progettoId: ProgettoId, numRegistrazioni: Int, ultimaAttivita: Instant); rimuovi(percorso: String) }
    - `VoceRegistro`: data class(progettoId: ProgettoId, nome: String, percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)
  - keys (minting rules):
    - `percorso`: minted by avvio-composizione (SessioneProgetto crea/apri): absolute path of the <nome>.snastro folder as an opaque string; the registry is keyed by it — a moved folder re-registers on open
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder

Sources: ADRs 0002, 0003, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S1 (+ R3).
