---
id: "allineatore"
type: "adapter"
context: "trascrizione"
side: "app"
wave: 11
release: "R1"
module: ":trascrizione:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-riconoscitore"
  - "tec-vad"
  - "tec-allineatore"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0012"
gated_by:
  - "ADR closing spike allineamento-parole-voci"
---
# allineatore — Allineatore (Kotlin puro) — strategia dallo spike

## What to do
Implement Allineatore with the strategy the spike ADR chooses (A: transcribe per turn, VAD-split > ~30 s; or B: token timestamps by midpoint), over RiconoscitoreParlato + Vad ports; testable in the gate with fakes.

## Tasks
- AC-246 AllineatoreContratto passa contro l'implementazione con RiconoscitoreParlato e Vad finti (nel gate)
- AC-247 Turni sovrapposti producono Segmenti sovrapposti, entrambi conservati
- AC-248 Un turno più lungo di 30 s è spezzato con il VAD (se strategia A)

## Dependencies
- **GATED — not ready until:** ADR closing spike allineamento-parole-voci
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
- **tec-riconoscitore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RiconoscitoreParlato`: interface { fun riconosci(c: CampioniAudio): Riconoscimento }
    - `Riconoscimento`: data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps
    - `Token`: data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples
- **tec-vad** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Vad`: interface { fun parlato(c: CampioniAudio): List<IntervalloMs> } — ordered, non-overlapping
- **tec-allineatore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `Allineatore`: interface { fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> } — the adapter is built with RiconoscitoreParlato + Vad, so strategy A or B fits the same port
    - `SegmentoGrezzo`: data class(voceIndice: Int, intervallo: IntervalloMs, testo: String) — overlaps preserved, never trimmed/dropped (INV-7, Q-4)
    - `Turno`: see tec-diarizzatore

Sources: ADRs 0002, 0003, 0004, 0012 (.mismagent/decisions/); spike allineamento-parole-voci, INV-7.
