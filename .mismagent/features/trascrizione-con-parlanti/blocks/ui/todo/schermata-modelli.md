---
id: "schermata-modelli"
type: "ui"
context: "ui"
side: "app"
wave: 8
module: ":ui (snastro.ui.modelli)"
consumes:
  - "kernel-pl"
  - "tec-shell-ui"
depends_on:
  - "ui-fondamenta"
related_adrs:
  - "0002"
  - "0003"
  - "0008"
  - "0010"
  - "0012"
consumes_rm: []
triggers:
  - "ScaricaModelli (via ServizioModelli.scarica)"
owns_boundaries:
  tec-modelli-ui:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ServizioModelli: "interface { val stato: StateFlow<StatoModelli>; fun scarica(); fun licenze(): List<LicenzaVista> }"
      StatoModelli: "sealed interface { Pronti; Mancanti(numero: Int, totaleByte: Long); InDownload(modelloId: String, scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreModelli) }"
      ErroreModelli: "sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ReteAssente; DownloadFallito(motivo: String) }"
      LicenzaVista: "data class(nome: String, ruolo: String, licenza: String, attribuzione: String)"
---
# schermata-modelli — S5 · Modelli (onboarding download + licenze)

## What to do
S5 (R10, added to the ux-proposal): shown at startup when models are missing; download with progress; hash error; no-network retry; 'Licenze dei modelli e librerie' list from the catalogue. Declares the ServizioModelli port (implemented in :avvio over :modelli).

### Consumes read-models: —
### Triggers: ScaricaModelli (via ServizioModelli.scarica)

## Tasks
- AC-227 Modelli mancanti → schermata con dimensione totale e pulsante 'Scarica'
- AC-228 Download con avanzamento per modello (byte scaricati / totali)
- AC-229 Hash errato → errore e nessun file installato, con 'Riprova'
- AC-230 Rete assente → messaggio e 'Riprova'
- AC-231 Licenze elencate dal catalogo (nome, ruolo, licenza, attribuzione)
- AC-232 Modelli pronti → la schermata non blocca l'app
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `ui-fondamenta` (wave 6)
- **tec-modelli-ui** (OWNED here — built before its consumers) — owner `schermata-modelli`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ServizioModelli`: interface { val stato: StateFlow<StatoModelli>; fun scarica(); fun licenze(): List<LicenzaVista> }
    - `StatoModelli`: sealed interface { Pronti; Mancanti(numero: Int, totaleByte: Long); InDownload(modelloId: String, scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreModelli) }
    - `ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ReteAssente; DownloadFallito(motivo: String) }
    - `LicenzaVista`: data class(nome: String, ruolo: String, licenza: String, attribuzione: String)
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
- **tec-shell-ui** (consumed/implemented) — owner `ui-fondamenta`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SessioneProgetto`: interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }
    - `ProgettoAperto`: data class(progettoId: ProgettoId, nome: String, percorso: String)
    - `ErroreSessione`: sealed interface : ErroreDominio (file ErroriSessione.kt) { NomeProgettoVuoto; CartellaGiaEsistente; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente }
    - `ApriEsterno`: interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }
    - `AggiornamentiVista`: interface { val cambiamenti: Flow<Cambiamento> }
    - `Cambiamento`: data class(registrazioneId: RegistrazioneId?) — null = everything may have changed
  - keys (minting rules):
    - `percorso`: see tec-registro-progetti

Sources: ADRs 0002, 0003, 0008, 0010, 0012 (.mismagent/decisions/); ADR 0008 (+ R10), UI/ux-proposal.md S5.
