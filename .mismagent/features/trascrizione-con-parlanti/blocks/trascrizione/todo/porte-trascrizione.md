---
id: "porte-trascrizione"
type: "port"
context: "trascrizione"
side: "app"
wave: 3
module: ":trascrizione:applicazione (..porte) + testFixtures"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0007"
  - "0012"
owns_boundaries:
  repo-trascrizione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      ElaborazioneRepository: "interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta | ElaborazioneGiaCompletata) from the ADR 0007 indexes */ }"
      TrascrittoRepository: "interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento"
  tec-decodifica-trascrizione:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      DecodificatoreAudio: "interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16"
  tec-diarizzatore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Diarizzatore: "interface { fun diarizza(c: CampioniAudio): List<Turno> }"
      Turno: "data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index"
  tec-riconoscitore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      RiconoscitoreParlato: "interface { fun riconosci(c: CampioniAudio): Riconoscimento }"
      Riconoscimento: "data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps"
      Token: "data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples"
  tec-vad:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Vad: "interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping"
  tec-allineatore:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      Allineatore: "interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port"
      SegmentoGrezzo: "data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)"
      Turno: "see tec-diarizzatore"
  tec-segnalatore-fase:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      SegnalatoreFase: "interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }"
      FaseElaborazione: "enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state"
---
# porte-trascrizione — Porte della Trascrizione: repository e pipeline

## What to do
Declare ElaborazioneRepository, TrascrittoRepository, DecodificatoreAudio, Diarizzatore, RiconoscitoreParlato, Vad, Allineatore, SegnalatoreFase (+ FaseElaborazione) with Finta + Contratto each; the ElaborazioneRepositoryFinta honours INV-4 like the indexes.

## Tasks
- AC-29 ElaborazioneRepositoryContratto: una seconda Elaborazione aperta per la stessa Registrazione → ElaborazioneGiaAperta; una seconda completata → ElaborazioneGiaCompletata; inAttesa in ordine FIFO di creazione (passa contro la Finta)
- AC-30 TrascrittoRepositoryContratto: round-trip completo incluse Voci, Segmenti, prossimaVoce e prossimoSegmento
- AC-31 DecodificatoreAudioContratto: campioni(intervallo) restituisce (fine - inizio) × 16 campioni
- AC-32 DiarizzatoreContratto: ogni Turno ha inizio < fine entro la durata e voceIndice >= 0
- AC-33 RiconoscitoreParlatoContratto: i token, se presenti, cadono dentro la durata dei campioni
- AC-34 VadContratto: intervalli ordinati, non sovrapposti, entro la durata
- AC-35 AllineatoreContratto: ogni SegmentoGrezzo ha inizio < fine entro la durata, un voceIndice presente nei turni, e le sovrapposizioni tra turni non vengono tagliate né eliminate
- AC-36 SegnalatoreFaseFinta registra la sequenza di fasi ricevute

## Dependencies
- **repo-trascrizione** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneRepository`: interface { diRegistrazione(id: RegistrazioneId): List<Elaborazione>; inAttesa(): List<Elaborazione> /* FIFO by creataAlle, tie id */; inCorso(): List<Elaborazione>; salva(e: Elaborazione): Esito<Unit> /* Errore(ElaborazioneGiaAperta | ElaborazioneGiaCompletata) from the ADR 0007 indexes */ }
    - `TrascrittoRepository`: interface { trova(id: RegistrazioneId): Trascritto?; conTrascritto(): List<RegistrazioneId>; salva(t: Trascritto) } — persists prossimaVoce / prossimoSegmento
- **tec-decodifica-trascrizione** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio); fun tutti(id: RegistrazioneId): CampioniAudio; fun campioni(id: RegistrazioneId, intervallo: IntervalloMs): CampioniAudio } — infra faults throw (ADR 0003); campioni count = (fine-inizio)*16
- **tec-diarizzatore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Diarizzatore`: interface { fun diarizza(c: CampioniAudio): List<Turno> }
    - `Turno`: data class(intervallo: IntervalloMs, voceIndice: Int) — voceIndice >= 0, diarizer cluster index
  - keys (minting rules):
    - `voceIndice`: minted by the Diarizzatore adapter per run — transient, NEVER persisted; the trascritto aggregate maps it to VoceId by first appearance
- **tec-riconoscitore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RiconoscitoreParlato`: interface { fun riconosci(c: CampioniAudio): Riconoscimento }
    - `Riconoscimento`: data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps
    - `Token`: data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples
- **tec-vad** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Vad`: interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping
- **tec-allineatore** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Allineatore`: interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port
    - `SegmentoGrezzo`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)
    - `Turno`: see tec-diarizzatore
- **tec-segnalatore-fase** (OWNED here — built before its consumers) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state
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

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0007, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/architetture/architecture-overview.md (Technical ports), ADR 0004.
