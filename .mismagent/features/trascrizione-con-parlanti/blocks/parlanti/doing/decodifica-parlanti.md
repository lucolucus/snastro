---
id: "decodifica-parlanti"
type: "adapter"
context: "parlanti"
side: "app"
wave: 5
release: "R2"
module: ":parlanti:adattatori (..audio)"
consumes:
  - "kernel-pl"
  - "tec-decodifica-parlanti"
  - "tec-audio-api"
depends_on:
  - "audio-ffmpeg"
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0010"
  - "0012"
---
# decodifica-parlanti — DecodificatoreAudio (parlanti) su :audio

## What to do
Thin adapter implementing DecodificatoreAudio (parlanti) over the :audio API, resolving project-relative paths (audio/<id>.<ext>, cache/audio/<id>.wav — rebuilt from the source if missing).

## Tasks
- AC-151 [@modelli] DecodificatoreAudioContratto (parlanti) passano contro l'adattatore reale su audio sintetico scritto dal test (un WAV generato a runtime; nessun file di sample/ nei test)
- AC-152 Un WAV derivato mancante viene ricostruito dalla sorgente copiata

## Dependencies
- Blocks built first: `audio-ffmpeg` (wave 4)
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
- **tec-decodifica-parlanti** (consumed/implemented) — owner `porte-parlanti`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `DecodificatoreAudio`: interface { fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio } — concatenation in the given order (Parlanti's own copy); NEVER called while a UnitaDiLavoro transaction is open (ADR 0012 Amendment (b))
    - `DecodificatoreAudioFinta`: testFixtures — takes the UnitaDiLavoroFinta (optional ctor param) and throws IllegalStateException when campioni is invoked while transazioneAperta
- **tec-audio-api** (consumed/implemented) — owner `audio-ffmpeg`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.audio.SondaFfmpeg`: fun sonda(file: Path): InfoFile(durataMs: Long, modificatoIl: LocalDate) — throws AudioIlleggibile / FormatoNonSupportato
    - `snastro.audio.DecodificaFfmpeg`: fun decodificaInWav(sorgente: Path, destinazione: Path) — 16 kHz mono 16-bit PCM; fun leggiCampioni(wav: Path, inizioMs: Long, fineMs: Long): FloatArray
    - `snastro.audio.RiproduttoreWav`: AutoCloseable { riproduci(wav: Path, intervalli: List<Pair<Long, Long>>?, daMs: Long); pausa(); posizioneMs(): Long; inRiproduzione(): Boolean }

Sources: ADRs 0002, 0003, 0005, 0010, 0012 (.mismagent/decisions/); ADR 0005/0010.
