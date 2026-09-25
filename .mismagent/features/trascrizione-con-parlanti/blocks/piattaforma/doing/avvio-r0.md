---
id: "avvio-r0"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 9
release: "R0"
module: ":avvio"
consumes:
  - "kernel-pl"
  - "eventi-progetto"
  - "tec-registro-progetti"
  - "tec-audio-api"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "ui-fondamenta"
  - "lettore-audio"
  - "schermata-progetti"
  - "schermata-registrazioni"
  - "persistenza-schema"
  - "registro-progetti-file"
  - "repository-sql-progetto"
  - "audio-progetto"
  - "audio-ffmpeg"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0010"
  - "0012"
  - "0014"
  - "0020"
---
# avvio-r0 — Composition root R0 (Archivio): main, sessione di progetto, import e riproduzione, S1/S2, smoke

## What to do
R0 composition root (release Archivio): main() with manual wiring of the R0 graph only — SessioneProgetto crea/apri/chiudi (folder naming AC-263/264/265, <nome>.snastro/ layout, .lock, apriDatabaseProgetto), RegistroProgetti on the per-OS app-data path with every call off the UI thread and errors logged, never aborting open/create/close; CreaProgetto/AggiungiRegistrazione/ModificaDataRegistrazione over eventi.unitaDiLavoro; ElencoProgetti + RegistrazioniDelProgetto; SondaAudio/ArchivioAudio for import; LettoreAudio over RiproduttoreWav (WAV rebuilt from the source); ApriEsterno over java.awt.Desktop; AggiornamentiVista fed by after-commit Progetto events; shell without the Parlanti section and S2 without Trascrizione sources; --smoke <fixture-dir> for S1/S2.

REWORK 2026-09-24 (ADR 0014): no behaviour change (R0 registers no sync subscriber on RegistrazioneAggiunta, and R1 will not either); fix the comments that call the auto-start of the Elaborazione a future (R2) behaviour: avvio/src/main/kotlin/snastro/avvio/AggiornamentiVistaEventi.kt and avvio/src/test/kotlin/snastro/avvio/AggiungiRegistrazioneR0Test.kt — it is removed, not deferred.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): R0 half of the former monolithic avvio-composizione. main(): manual wiring of the R0 graph only — SessioneProgetto crea/apri/chiudi (folder naming AC-263/264/265, .lock, apriDatabaseProgetto), RegistroProgetti (per-OS path AC-348, calls off the UI thread, errors logged and never aborting AC-347), CreaProgetto/AggiungiRegistrazione/ModificaDataRegistrazione over eventi.unitaDiLavoro (AC-346), ElencoProgetti + RegistrazioniDelProgetto read-models, SondaAudio/ArchivioAudio (audio-progetto) for import, LettoreAudio over RiproduttoreWav (WAV rebuilt from the source into cache/audio/, AC-241), ApriEsterno over java.awt.Desktop, AggiornamentiVista fed by after-commit Progetto events (AC-242), shell WITHOUT the Parlanti section (AC-341) and S2 WITHOUT Trascrizione sources (AC-342), --smoke for S1/S2 (AC-237). Carry-overs owned here: registry errors never abort open/create/close and never on the UI thread (fix-batch-6 code-review, mandatory); per-OS app-data path of the registry (registro-progetti-file code-review); eventi.unitaDiLavoro wiring (revisione code-review) — the same rule binds avvio-composizione and avvio-parlanti. USER DECISION 2026-09-23 (folder naming) as recorded on AC-263..265: 60 code points keeps <nome> (NN).snastro within the 255-byte NAME_MAX of APFS/ext4 and NTFS's 255 UTF-16 units; SessioneProgetto.crea never produces CartellaGiaEsistente. The later blocks EXTEND this wiring (they never re-create SessioneProgetto, the dispatcher or LettoreAudio): avvio-composizione (R1) adds Trascrizione/Documento/Modelli, avvio-parlanti (R2) adds Parlanti. Pre-release (not gating): audio-ffmpeg fix-batch-7 (atomic WAV write, RiproduttoreWav playback token/position/flush) improves R0 playback robustness.

## Tasks
- AC-237 --smoke apre il progetto fixture (con almeno una Registrazione importata) e salva uno screenshot di S1 e di S2 in avvio/build/smoke/, uscendo con 0 — senza nativi sherpa né modelli installati (R0); avvio-composizione e avvio-parlanti estendono lo smoke alle loro schermate
- AC-238 Una seconda istanza sullo stesso progetto riceve 'progetto già aperto'
- AC-239 SessioneProgetto.crea(cartellaGenitore, nome) crea in cartellaGenitore la cartella <nomeCartella>.snastro/ scelta secondo AC-263/AC-264, con audio/, documenti/, cache/audio/, progetto.db e .lock, e registra il progetto nel RegistroProgetti con percorso = percorso assoluto della cartella effettivamente creata
- AC-263 Il nome della cartella deriva da NomeProgetto (funzione pura, test a tabella): ogni carattere non valido su Windows/macOS/Linux (< > : " / \ | ? * e i caratteri di controllo U+0000–U+001F, U+007F) → '_'; spazi e punti iniziali/finali rimossi; troncato a 60 code point (mai spezzando una coppia surrogata) e poi di nuovo ripulito da spazi/punti finali; un nome riservato Windows (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9, senza distinzione di maiuscole) riceve un '_' finale; risultato vuoto → 'progetto'; il suffisso '.snastro' è aggiunto dopo. Es.: 'Riunione 3/10: budget?' → 'Riunione 3_10_ budget_.snastro'; 'con' → 'con_.snastro'; '...' → 'progetto.snastro'; un nome di 80 caratteri → 60 caratteri + '.snastro'
- AC-264 Se <nomeCartella>.snastro esiste già in cartellaGenitore (cartella o file, con la semantica del filesystem: su APFS case-insensitive 'Budget' collide con 'budget'), SessioneProgetto.crea usa '<nomeCartella> (2).snastro', poi ' (3)'… fino al primo nome libero: mai errore, mai sovrascrittura né scrittura dentro la cartella preesistente (il suo contenuto resta byte-identico); la scelta è race-safe (createDirectory atomico, non exists-poi-crea: se il nome viene occupato nel frattempo si passa al numero successivo)
- AC-265 Il Progetto mantiene il nome digitato: CreaProgetto riceve il nome digitato (solo il trim di NomeProgetto, AC-16), mai il nome della cartella; ProgettoAperto.nome, VoceRegistro.nome e ProgettoCreato.nome valgono es. 'Riunione 3/10: budget?' mentre percorso termina con 'Riunione 3_10_ budget_ (2).snastro'; nome vuoto o di soli spazi → NomeProgettoVuoto e nessuna cartella creata
- AC-240 Alla chiusura il registro è aggiornato con numRegistrazioni e ultimaAttivita
- AC-241 Il LettoreAudio ricostruisce un WAV derivato mancante dalla sorgente; senza sorgente riporta non disponibile
- AC-242 Un comando completato produce un Cambiamento su AggiornamentiVista per la sua Registrazione — in R0: AggiungiRegistrazione e ModificaDataRegistrazione (dopo il commit, mai su rollback); S2 si aggiorna senza ricaricare a mano
- AC-350 (R0, vedi AC-341/AC-342) La composizione R0 non fornisce la sezione Parlanti alla shell, costruisce S2 SENZA sorgenti di Trascrizione (AC-342) e non rende raggiungibili S3/S4/S5: nessun abbonato né servizio di Trascrizione, Documento, Parlanti o Modelli è registrato; dopo AggiungiRegistrazione nessuna riga esiste in elaborazione; :avvio parte senza caricare i nativi sherpa e senza modelli installati
- AC-346 Ogni servizio di comando (CreaProgetto, AggiungiRegistrazione, ModificaDataRegistrazione) è costruito con eventi.unitaDiLavoro (la UnitaDiLavoro del DispatcherEventiInMemoria), mai con il delegato grezzo: test end-to-end su databaseInMemoria — AggiungiRegistrazione pubblica RegistrazioneAggiunta senza errore e un abbonato dopo-commit registrato nel test la riceve solo dopo il commit (e mai dopo un rollback)
- AC-347 Le chiamate al RegistroProgetti (registra, aggiorna, elenco, rimuovi) girano FUORI dal thread UI (il lock di file di AC-328 blocca) e un loro errore o eccezione (IOException, lock, file illeggibile) è registrato nel log e non annulla MAI crea/apri/chiudi: con un RegistroProgetti finto che lancia sempre, crea e apri restituiscono Ok con il progetto aperto, chiudi rilascia comunque il .lock, S1 mostra un elenco vuoto
- AC-348 Il file del registro dei progetti recenti sta nella cartella dati per utente del sistema operativo (funzione pura con os, env e user.home iniettati, stessa regola di AC-133/AC-334/AC-335 senza il suffisso modelli): macOS ~/Library/Application Support/snastro/; Windows <LOCALAPPDATA>\snastro\ (LOCALAPPDATA assente, vuoto o relativo → <user.home>\AppData\Local\snastro\); Linux <XDG_DATA_HOME>/snastro/ (assente, vuoto o relativo → <user.home>/.local/share/snastro/); mai dentro una cartella di progetto — test a tabella
- AC-349 SessioneProgetto.apri: una cartella senza progetto.db o non leggibile → CartellaNonValida; un progetto.db con versione di schema più recente → DatabasePiuRecente, senza modificarlo né lasciare il .lock; un progetto spostato viene aperto e ri-registrato con il nuovo percorso assoluto; chiudi rilascia il .lock così che la riapertura (anche da un'altra istanza) riesce

## Dependencies
- Blocks built first: `ui-fondamenta` (wave 6), `lettore-audio` (wave 7), `schermata-progetti` (wave 8), `schermata-registrazioni` (wave 8), `persistenza-schema` (wave 2), `registro-progetti-file` (wave 4), `repository-sql-progetto` (wave 6), `audio-progetto` (wave 5), `audio-ffmpeg` (wave 4)
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
- **eventi-progetto** (consumed/implemented) — owner `eventi-pubblicati`, supplier `crea-progetto, servizi-registrazione, RinominaRegistrazione (progetto:applicazione, fix-batch-11), elimina-registrazione (RegistrazioneEliminata, ADR 0020)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoCreato`: data class(progettoId: ProgettoId, nome: String) : EventoPubblicato
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — AFTER-COMMIT consumers only (view refresh); NO synchronous subscriber (no automatic start on import, ADR 0014 / ADR 0012 Amendment (c))
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
    - `RegistrazioneRinominata`: data class(registrazioneId: RegistrazioneId, precedente: String, nuovo: String) : EventoPubblicato — precedente/nuovo = the titolo before/after RinominaRegistrazione (fix-batch-11, AC-360/361 in tasks/app/done/r0-feedback-1.md); AFTER-COMMIT consumers: AggiornamentiVista (avvio-r0, AC-366) and abbonato-documento (AC-186bis)
    - `RegistrazioneEliminata`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) : EventoPubblicato — published by EliminaRegistrazione INSIDE its transaction, BEFORE the registrazione row is removed; titolo/data/riferimento are the values at deletion (the only way after-commit consumers can locate the files). SYNCHRONOUS consumers: abbonato-eliminazione-trascrizione (veto + purge), abbonato-revisione-parlanti (Parlanti purge + INV-25); AFTER-COMMIT consumers: abbonato-documento (.md removal on the per-key queue), avvio-parlanti (AggiornamentiVistaParlanti, PuliziaRegistrazioneEliminata). ADR 0020
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: All four events → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. AMENDED 2026-09-24 (ADR 0014 / ADR 0012 Amendment (c)): the SYNCHRONOUS clause for RegistrazioneAggiunta is dropped — it has no sync subscriber (the dispatcher's sync mechanism itself is unchanged, ADR 0012). AMENDED 2026-09-24 (delta 2026-09-24-rinomina-documento): RegistrazioneRinominata pinned (already published by the merged code). EXCEPTION (ADR 0020, 2026-09-25): RegistrazioneEliminata has TWO synchronous subscribers (Trascrizione veto + purge, Parlanti purge) inside the publishing transaction; an Errore from either dooms the command and is returned unchanged by EliminaRegistrazione; its other subscribers are after commit (same at-least-once / idempotent rules; abbonato-documento serializes it on the per-registrazioneId queue behind any in-flight Rigenerazione)
- **tec-registro-progetti** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RegistroProgetti`: interface { elenco(): List<VoceRegistro> /* by ultimaAttivita desc */; registra(v: VoceRegistro); aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) /* keyed by percorso like registra/rimuovi; unknown percorso → no-op */; rimuovi(percorso: String) }
    - `VoceRegistro`: data class(progettoId: ProgettoId, nome: String, percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)
  - keys (minting rules):
    - `percorso`: minted by avvio-r0 (SessioneProgetto crea/apri): absolute path of the <nome>.snastro folder as an opaque string; the registry is keyed by it — a moved folder re-registers on open
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
- **tec-audio-api** (consumed/implemented) — owner `audio-ffmpeg`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.audio.SondaFfmpeg`: fun sonda(file: Path): InfoFile(durataMs: Long, modificatoIl: LocalDate) — throws AudioIlleggibile / FormatoNonSupportato
    - `snastro.audio.DecodificaFfmpeg`: fun decodificaInWav(sorgente: Path, destinazione: Path) — 16 kHz mono 16-bit PCM; fun leggiCampioni(wav: Path, inizioMs: Long, fineMs: Long): FloatArray
    - `snastro.audio.RiproduttoreWav`: AutoCloseable { riproduci(wav: Path, intervalli: List<Pair<Long, Long>>?, daMs: Long); pausa(); posizioneMs(): Long; inRiproduzione(): Boolean }
- **tec-lettore-audio** (consumed/implemented) — owner `lettore-audio`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreAudio`: interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }
    - `StatoLettore`: data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)
- **tec-shell-ui** (consumed/implemented) — owner `ui-fondamenta`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SessioneProgetto`: interface { val corrente: StateFlow<ProgettoAperto?>; fun crea(cartellaGenitore: String, nome: String): Esito<ProgettoAperto>; fun apri(percorso: String): Esito<ProgettoAperto>; fun chiudi() }
    - `ProgettoAperto`: data class(progettoId: ProgettoId, nome: String, percorso: String)
    - `ErroreSessione`: sealed interface : ErroreDominio (file ErroriSessione.kt) { NomeProgettoVuoto; CartellaNonValida; ProgettoGiaAperto; DatabasePiuRecente } — no CartellaGiaEsistente: crea derives a free folder name (AC-264), re-pinned 2026-09-23 (user decision)
    - `ApriEsterno`: interface { fun apriFile(percorso: String); fun mostraNellaCartella(percorso: String) }
    - `AggiornamentiVista`: interface { val cambiamenti: Flow<Cambiamento> }
    - `Cambiamento`: data class(registrazioneId: RegistrazioneId?) — null = everything may have changed
  - keys (minting rules):
    - `percorso`: see tec-registro-progetti

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0010, 0012, 0014, 0020 (.mismagent/decisions/); architecture.md (:avvio), ADR 0010 (+ R3, R15), profile run binding, user decisions 2026-09-23 (folder naming; release pivot R0 Archivio).
