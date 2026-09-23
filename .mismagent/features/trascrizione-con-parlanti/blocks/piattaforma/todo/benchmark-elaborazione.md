---
id: "benchmark-elaborazione"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 13
module: ":avvio (task benchmarkElaborazione)"
consumes:
  - "kernel-pl"
depends_on:
  - "diarizzatore-sherpa"
  - "riconoscitore-sherpa"
  - "vad-silero"
  - "allineatore"
  - "estrattore-impronta-sherpa"
  - "avvio-composizione"
related_adrs:
  - "0002"
  - "0003"
  - "0011"
  - "0012"
gated_by:
  - "all five spike ADRs"
  - "diarizzatore-sherpa, riconoscitore-sherpa, vad-silero, allineatore, estrattore-impronta-sherpa merged"
---
# benchmark-elaborazione — Benchmark NFR dell'Elaborazione (opt-in)

## What to do
Wire ./gradlew benchmarkElaborazione -Pcampione=<path> to run one real Elaborazione with the real adapters, print per-phase timings and fail above 600 s (ADR 0011, R17). Not part of check.

## Tasks
- AC-261 [opt-in, fuori gate] su un campione reale di 60 minuti con modelli scaricati, l'Elaborazione completa va da in_corso a completata in <= 600 s sull'M3 Pro; i tempi per fase sono stampati
- AC-262 Il task fallisce se il tempo supera 600 s

## Dependencies
- **GATED — not ready until:** all five spike ADRs; diarizzatore-sherpa, riconoscitore-sherpa, vad-silero, allineatore, estrattore-impronta-sherpa merged
- Blocks built first: `diarizzatore-sherpa` (wave 12), `riconoscitore-sherpa` (wave 12), `vad-silero` (wave 12), `allineatore` (wave 11), `estrattore-impronta-sherpa` (wave 12), `avvio-composizione` (wave 10)
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

Sources: ADRs 0002, 0003, 0011, 0012 (.mismagent/decisions/); ADR 0011 (+ R17).
