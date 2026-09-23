---
id: "avvio-coda-elaborazioni"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 9
module: ":avvio"
consumes:
  - "kernel-pl"
  - "tec-segnalatore-fase"
  - "tec-modelli"
depends_on:
  - "esegui-elaborazione"
  - "stati-elaborazione"
  - "modelli-provisioning"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0012"
---
# avvio-coda-elaborazioni — Coda seriale delle Elaborazioni e dispatcher della pipeline

## What to do
Serial FIFO on a single-thread pipeline dispatcher that calls EseguiProssimaElaborazione; holds while ModelliPronti is false; calls RecuperaElaborazioniInterrotte before starting; wires FasiInCorso as SegnalatoreFase; one Mutex serializes every native use (pipeline + EstrattoreImpronta, R12).

## Tasks
- AC-233 All'avvio RecuperaElaborazioniInterrotte gira prima che la coda parta
- AC-234 Con due in_attesa la seconda parte solo quando la prima è terminale
- AC-235 Con i modelli mancanti le Elaborazioni restano in_attesa; quando diventano pronti la coda riparte
- AC-236 Un'estrazione d'impronta richiesta dalla UI durante un'Elaborazione attende il Mutex nativo (nessuna chiamata nativa concorrente)

## Dependencies
- Blocks built first: `esegui-elaborazione` (wave 4), `stati-elaborazione` (wave 5), `modelli-provisioning` (wave 4)
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
- **tec-segnalatore-fase** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state
- **tec-modelli** (consumed/implemented) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String, dimensioneByte: Long, licenza: String, attribuzione: String)
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit> /* ErroreModelli (sealed : ErroreDominio, file ErroriModelli.kt): HashNonValido(id) | ReteAssente | DownloadFallito(motivo) */; fun percorso(id: String): Path
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across catalogue edits

Sources: ADRs 0002, 0003, 0004, 0008, 0012 (.mismagent/decisions/); ADR 0004/0008/0012 (+ R12).
