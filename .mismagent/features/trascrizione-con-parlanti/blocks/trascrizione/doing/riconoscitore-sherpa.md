---
id: "riconoscitore-sherpa"
type: "adapter"
context: "trascrizione"
side: "app"
wave: 12
release: "R1"
module: ":ml-sherpa + :trascrizione:adattatori (..ml)"
consumes:
  - "kernel-pl"
  - "tec-riconoscitore"
  - "tec-ml-sherpa"
depends_on:
  - "avvio-composizione"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0012"
  - "0013"
  - "0015"
  - "0016"
  - "0017"
gated_by:
  - "ADR closing spike scelta-asr-code-switching — satisfied: ADR 0013 (accepted)"
---
# riconoscitore-sherpa — RiconoscitoreParlato reale su sherpa-onnx

## What to do
Real RiconoscitoreParlato adapter with the model chosen by the spike ADR (catalogue entry URL + SHA-256 + licence added to :modelli in the same block); registered in :avvio's adapter-selection config (W12 blocks merged serially: they share that config file).

Note: AMENDED 2026-09-24 (ADR 0015): about 1000 per-turn calls per hour — reloading the model per call (0.9 s) would break ADR 0011, hence AC-388. ADR 0016: native load via MotoreSherpa (tec-ml-sherpa), never here.

## Tasks
- AC-252 [@modelli] RiconoscitoreParlatoContratto passa contro l'adattatore reale su un campione di sample/
- AC-253 La voce di catalogo di :modelli asr-parakeet-tdt-0.6b-v3-int8 (TAR_BZ2; file usati encoder.int8.onnx, decoder.int8.onnx, joiner.int8.onnx, tokens.txt) ha url, sha256, dimensioneByte, licenza (CC-BY-4.0) e attribuzione esattamente come nella tabella di ADR 0013 § ':modelli catalogue entries' — REWRITTEN 2026-09-24
- AC-254 Tutte le risorse native sono rilasciate a fine uso (use {})
- AC-388 [@modelli] (ADR 0015 Consequences) Il modello ASR è caricato una sola volta per Elaborazione, riusato per tutte le chiamate per turno di quella Elaborazione e rilasciato alla sua fine (mai un caricamento per chiamata): N chiamate a riconosci nella stessa Elaborazione → 1 caricamento

## Dependencies
- **GATED — not ready until:** ADR closing spike scelta-asr-code-switching — satisfied: ADR 0013 (accepted)
- Blocks built first: `avvio-composizione` (wave 10)
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
- **tec-riconoscitore** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RiconoscitoreParlato`: interface { fun riconosci(c: CampioniAudio): Riconoscimento }
    - `Riconoscimento`: data class(testo: String, token: List<Token>?) — token null if the model gives no timestamps
    - `Token`: data class(testo: String, intervallo: IntervalloMs) — relative to the start of the given samples
- **tec-ml-sherpa** (consumed/implemented) — owner `ml-sherpa-motore`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.ml.MotoreSherpa`: fun caricaNativi(); fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T — AutoCloseable released after use; holds ONE process-wide FAIR Mutex for one native session (ONE native call at a time); the wait is INTERRUPTIBLE: an interrupt while waiting → InterruptedException, and no session, no native load and no uso run; not reentrant; the Mutex is released on return or exception; every adapter holds it for ONE port call only (ADR 0017 §1)
    - `ConfigSessione`: data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = "cpu")

Sources: ADRs 0002, 0003, 0004, 0008, 0012, 0013, 0015, 0016, 0017 (.mismagent/decisions/); spike scelta-asr-code-switching, ADR 0004/0008/0013/0015/0016.
