---
id: "rigenerazione-documento"
type: "application-service"
context: "documento"
side: "app"
wave: 5
module: ":documento:applicazione (..politiche)"
consumes:
  - "kernel-pl"
  - "trascritto-per-documento"
  - "nomi-per-documento"
  - "tec-scrittore-documento"
depends_on:
  - "documento"
related_adrs:
  - "0002"
  - "0003"
  - "0010"
  - "0012"
commands:
  - "RigeneraDocumento"
  - "RigeneraTuttiIDocumenti"
---
# rigenerazione-documento — Rigenerazione del Documento

## What to do
Policy/service: RigeneraDocumento(registrazioneId, dataPrecedente?) writes the documento projection under nomeFile and, if the date changed, removes the old file after; RigeneraTuttiIDocumenti (startup, R4) regenerates every Registrazione with a Trascritto; maps each event of the event boundaries to the affected Registrazioni.

## Tasks
- AC-153 RigeneraDocumento scrive il markdown della proiezione con il nomeFile corretto
- AC-154 RigeneraDocumento su una Registrazione senza Trascritto → nessuna scrittura, Ok
- AC-155 Con una DataRegistrazioneModificata il nuovo file nomeFile(nuova, titolo) è scritto e poi il vecchio nomeFile(precedente, titolo) è rimosso — entrambi calcolati con la stessa funzione pulita di documento (AC-320)
- AC-327 Il vecchio file rimosso non appartiene mai a un'altra Registrazione: con 'Riunione' (data 2026-09-12) e 'Riunione (2)' (data 2026-09-13) spostare la data di 'Riunione (2)' al 2026-09-12 scrive '2026-09-12 Riunione (2).md', rimuove '2026-09-13 Riunione (2).md' e non tocca '2026-09-12 Riunione.md'; se precedente = nuova il file è riscritto e nulla è rimosso
- AC-156 ParlanteRinominato → rigenerate tutte e sole le Registrazioni con un'Attribuzione a P; ParlantePromosso con nomeCambiato = false → nulla; ParlanteEliminato → nulla
- AC-157 Un errore di scrittura → Esito.Errore (così l'abbonato può riprovare)
- AC-158 RigeneraTuttiIDocumenti rigenera ogni Registrazione con Trascritto

## Dependencies
- Blocks built first: `documento` (wave 4)
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
- **trascritto-per-documento** (consumed/implemented) — owner `porta-lettore-trascritto`, supplier `api-trascritto + catalogo-registrazioni`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreTrascritto`: interface { fun trascritto(id: RegistrazioneId): TrascrittoTesto?; fun registrazioniConTrascritto(): List<RegistrazioneId> }
    - `TrascrittoTesto`: data class(registrazioneId: RegistrazioneId, titolo: String, dataRegistrazione: LocalDate, segmenti: List<SegmentoVista>)
    - `SegmentoVista`: data class(segmentoId: SegmentoId, voceId: VoceId, intervallo: IntervalloMs, testo: String) — list ordered by inizioMs then segmentoId ACROSS Voci; testo verbatim
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable forever
- **nomi-per-documento** (consumed/implemented) — owner `porta-lettore-nomi`, supplier `nomi-delle-voci`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreNomi`: interface { fun nomi(id: RegistrazioneId): Map<VoceRef, String>; fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> } — attributed Voci only; an eliminato Parlante still resolves to its Nome (INV-24)
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
- **tec-scrittore-documento** (consumed/implemented) — owner `porte-documento`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ScrittoreDocumento`: interface { fun scrivi(nomeFile: String, markdown: String); fun rimuovi(nomeFile: String) } — write-only, never reads (ADR 0010)
  - keys (minting rules):
    - `nomeFile`: minted by documento.nomeFile(dataRegistrazione, titolo) = '<AAAA-MM-DD> ' + pulisci(titolo) + '.md' under documenti/, with pulisci(t) — the AC-263 rule applied to a Documento titolo: NFC-normalize; every character invalid on Windows/macOS/Linux (< > : " / \ | ? * and the control characters U+0000–U+001F, U+007F) → '_'; leading/trailing spaces and dots removed; truncated to 237 UTF-8 bytes on a code-point boundary (never splitting a surrogate pair) and trailing spaces/dots removed again; a Windows reserved name (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9, case-insensitive) gets a trailing '_' (kept for identity with AC-263 although the date prefix already neutralizes it); empty result → 'registrazione'. 237 = 255 − 11 ('AAAA-MM-DD ') − 3 ('.md') − 4 ('.tmp' of the atomic write), in UTF-8 bytes, which also bounds NTFS's 255 UTF-16 units. UNIQUE per Progetto: titolo is unique per Progetto on the key pulisci(titolo).lowercase(Locale.ROOT) (servizi-registrazione AC-322) — so two Registrazioni never share a nomeFile whatever their dates; changes only when dataRegistrazione changes (titolo immutable). The atomic write's temp file is '<nomeFile>.tmp' in the same folder (AC-340).

Sources: ADRs 0002, 0003, 0010, 0012 (.mismagent/decisions/); features/trascrizione-con-parlanti/tactical-model.md § Documento Policy (+ R4, R5), ADR 0012.
