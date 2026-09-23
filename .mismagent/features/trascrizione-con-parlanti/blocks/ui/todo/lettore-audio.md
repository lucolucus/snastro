---
id: "lettore-audio"
type: "ui"
context: "ui"
side: "app"
wave: 7
module: ":ui (snastro.ui.lettore)"
consumes:
  - "kernel-pl"
  - "tec-shell-ui"
depends_on:
  - "estratto-audio"
  - "ui-fondamenta"
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0010"
  - "0012"
consumes_rm:
  - "estratto-audio"
triggers: []
owns_boundaries:
  tec-lettore-audio:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      LettoreAudio: "interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }"
      StatoLettore: "data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)"
---
# lettore-audio — Componente lettore-audio condiviso

## What to do
Shared audio bar + '▶' control: LettorePresenter over the LettoreAudio port (declared here); play/pause, position, extract as a sequence of intervals, unavailable state.

### Consumes read-models: estratto-audio
### Triggers: —

## Tasks
- AC-187 play/pausa funzionano e la posizione avanza durante la riproduzione
- AC-188 Un estratto riproduce i suoi intervalli in sequenza e si ferma alla fine dell'ultimo
- AC-189 Sorgente non disponibile → controlli disabilitati con messaggio
- AC-190 Un nuovo play sostituisce la riproduzione in corso
- AC-191 Stato di caricamento (WAV in preparazione) mostrato finché il lettore non è pronto
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `estratto-audio` (wave 4), `ui-fondamenta` (wave 6)
- **tec-lettore-audio** (OWNED here — built before its consumers) — owner `lettore-audio`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreAudio`: interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }
    - `StatoLettore`: data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)
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

Sources: ADRs 0002, 0003, 0005, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md (lettore-audio), ADR 0005.
