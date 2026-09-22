---
id: "schermata-registrazione"
type: "ui"
context: "ui"
side: "app"
wave: 8
module: ":ui (snastro.ui.registrazione)"
consumes:
  - "kernel-pl"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "trascritto-view"
  - "identificazione-voci"
  - "proposta"
  - "proposta-unione"
  - "parlanti-attivi"
  - "estratto-audio"
  - "documento"
  - "conferma-attribuzione"
  - "salta-voce"
  - "revisione"
  - "ui-fondamenta"
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0010"
  - "0012"
consumes_rm:
  - "trascritto-view"
  - "identificazione-voci"
  - "proposta"
  - "proposta-unione"
  - "parlanti-attivi"
  - "estratto-audio"
  - "documento"
triggers:
  - "ConfermaAttribuzione"
  - "SaltaVoce"
  - "UnisciVoci"
  - "DividiVoce"
  - "RiassegnaSegmento"
---
# schermata-registrazione — S3 · Registrazione (identificazione + Revisione)

## What to do
S3 concept B: header with audio bar + 'Apri documento' / 'Mostra nella cartella' (ApriEsterno + documento.nomeFile), transcript centre, Voci panel right; presenter joins trascritto-view + identificazione-voci (R1).

Note: Declared: Documento content is never shown in-app (ux decision); 'salta' is not offered on an attributed Voce (use 'cambia', R24).

### Consumes read-models: trascritto-view, identificazione-voci, proposta, proposta-unione, parlanti-attivi, estratto-audio, documento
### Triggers: ConfermaAttribuzione, SaltaVoce, UnisciVoci, DividiVoce, RiassegnaSegmento

## Tasks
- AC-207 Caricamento: scheletro del trascritto
- AC-208 Click su un Segmento → riproduzione dal suo inizio e Segmento evidenziato
- AC-209 La selezione è limitata a una Voce
- AC-210 'Dividi voce' disabilitato con spiegazione se la selezione è l'intera Voce
- AC-211 Riassegna verso una Voce esistente o 'nuova voce'
- AC-212 Galleria vuota → solo 'nuovo…' / 'salta' con il suggerimento 'Prima registrazione: dai un nome alle voci'
- AC-213 Tutti i Candidati 'nessuna' → lista mostrata e 'nuovo…' evidenziato
- AC-214 Fascia come barra, mai un numero
- AC-215 Errore di comando (es. nome già usato) → messaggio inline sulla card e nulla cambia
- AC-216 Banner di proposta di unione: un click unisce e il banner scompare quando la condizione cade
- AC-217 Audio sorgente mancante → barra audio ed estratti disabilitati con messaggio, trascritto usabile
- AC-218 'Apri documento' e 'Mostra nella cartella' usano il percorso del Documento
- AC-219 Una Voce attribuita mostra 'cambia' e non 'salta'
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `trascritto-view` (wave 5), `identificazione-voci` (wave 5), `proposta` (wave 5), `proposta-unione` (wave 5), `parlanti-attivi` (wave 5), `estratto-audio` (wave 4), `documento` (wave 4), `conferma-attribuzione` (wave 4), `salta-voce` (wave 4), `revisione` (wave 4), `ui-fondamenta` (wave 6)
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
    - `ErroreDominio`: interface (NOT sealed — Kotlin forbids cross-module sealed subtypes; NOT Throwable); each context declares its own sealed hierarchy Errori<Contesto> : ErroreDominio
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
- **tec-lettore-audio** (consumed/implemented) — owner `lettore-audio`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreAudio`: interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }
    - `StatoLettore`: data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)
- **tec-shell-ui** (consumed/implemented) — owner `ui-fondamenta`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SessioneProgetto`: interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }
    - `ProgettoAperto`: data class(progettoId: ProgettoId, nome: String, percorso: String)
    - `ErroriSessione`: sealed interface : ErroreDominio { NomeProgettoVuoto; CartellaGiaEsistente; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente }
    - `ApriEsterno`: interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }
    - `AggiornamentiVista`: interface { val cambiamenti: Flow<Cambiamento> }
    - `Cambiamento`: data class(registrazioneId: RegistrazioneId?) — null = everything may have changed
  - keys (minting rules):
    - `percorso`: see tec-registro-progetti

Sources: ADRs 0002, 0003, 0005, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S3 (+ R1, R8, R24).
