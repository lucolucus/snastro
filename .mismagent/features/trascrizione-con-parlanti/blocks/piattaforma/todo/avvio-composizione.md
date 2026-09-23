---
id: "avvio-composizione"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 10
module: ":avvio"
consumes:
  - "kernel-pl"
  - "eventi-progetto"
  - "eventi-elaborazione"
  - "eventi-revisione"
  - "eventi-parlanti"
  - "tec-registro-progetti"
  - "tec-audio-api"
  - "tec-modelli"
  - "tec-lettore-audio"
  - "tec-shell-ui"
  - "tec-modelli-ui"
depends_on:
  - "avvio-coda-elaborazioni"
  - "ui-fondamenta"
  - "lettore-audio"
  - "schermata-progetti"
  - "schermata-registrazioni"
  - "schermata-registrazione"
  - "schermata-parlanti"
  - "schermata-modelli"
  - "persistenza-schema"
  - "registro-progetti-file"
  - "repository-sql-progetto"
  - "repository-sql-trascrizione"
  - "repository-sql-parlanti"
  - "abbonato-documento"
  - "abbonato-registrazione-aggiunta"
  - "abbonato-revisione-parlanti"
  - "abbonato-riallineamento-impronte"
  - "riallinea-impronte"
  - "audio-ffmpeg"
  - "modelli-provisioning"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0006"
  - "0008"
  - "0010"
  - "0012"
---
# avvio-composizione — Composition root, sessione di progetto, adattatori UI, smoke

## What to do
main(): manual wiring; SessioneProgetto (folder layout <nome>.snastro/, .lock, apriDatabaseProgetto, CreaProgetto, RegistroProgetti registra/aggiorna on close); registration of sync/after-commit subscribers; LettoreAudio over RiproduttoreWav (rebuilds a missing WAV, else unavailable); ApriEsterno over java.awt.Desktop; AggiornamentiVista fed by after-commit events + phase changes; ServizioModelli over :modelli; config-driven adapter selection (fakes until the ML blocks land); --smoke <fixture-dir> with a fixture-project generator.

Note: USER DECISION 2026-09-23 (dispatch.log): project folder name derived from NomeProgetto by cleaning (AC-263), ' (n)' on an existing folder (AC-264), the Progetto keeps the typed name (AC-265) — 60 code points keeps <nome> (NN).snastro within the 255-byte NAME_MAX of APFS/ext4 (60×4 UTF-8 bytes + 13) and within NTFS's 255 UTF-16 units, leaving room for the 260-char Windows MAX_PATH under the default parent; Windows reserved names are handled because the Windows route stays open (ADR 0010). SessioneProgetto.crea no longer produces ErroreSessione.CartellaGiaEsistente (variant removed from tec-shell-ui's pinned ErroreSessione, re-pinned 2026-09-23, user decision). CARRY-OVER (revisione code-review): wire each command service with `eventi.unitaDiLavoro` (DispatcherEventiInMemoria's own UnitaDiLavoro), never the raw delegate — otherwise pubblica fails at runtime. AMENDED 2026-09-23 (ADR 0012 Amendment (b)): wire abbonato-riallineamento-impronte (after commit) and run RiallineaTutteLeImpronte at project open, in background, after RecuperaElaborazioniInterrotte; pass the same UnitaDiLavoro to the Parlanti commands and to RiallineaImpronte. AMENDED 2026-09-23: tec-registro-progetti re-pinned to aggiorna(percorso, …) — AC-240 updates the registry entry by the session's percorso; tec-modelli-ui error type is now the UI-side ErroreServizioModelli, mapped here from :modelli (AC-329); tec-modelli percorso(id) is a DIRECTORY (ADR 0008 Amendment (c)).

## Tasks
- AC-237 --smoke apre il progetto fixture e salva uno screenshot per ogni schermata S1–S5 in avvio/build/smoke/, uscendo con 0
- AC-238 Una seconda istanza sullo stesso progetto riceve 'progetto già aperto'
- AC-239 SessioneProgetto.crea(cartellaGenitore, nome) crea in cartellaGenitore la cartella <nomeCartella>.snastro/ scelta secondo AC-263/AC-264, con audio/, documenti/, cache/audio/, progetto.db e .lock, e registra il progetto nel RegistroProgetti con percorso = percorso assoluto della cartella effettivamente creata
- AC-263 Il nome della cartella deriva da NomeProgetto (funzione pura, test a tabella): ogni carattere non valido su Windows/macOS/Linux (< > : " / \ | ? * e i caratteri di controllo U+0000–U+001F, U+007F) → '_'; spazi e punti iniziali/finali rimossi; troncato a 60 code point (mai spezzando una coppia surrogata) e poi di nuovo ripulito da spazi/punti finali; un nome riservato Windows (CON, PRN, AUX, NUL, COM1–COM9, LPT1–LPT9, senza distinzione di maiuscole) riceve un '_' finale; risultato vuoto → 'progetto'; il suffisso '.snastro' è aggiunto dopo. Es.: 'Riunione 3/10: budget?' → 'Riunione 3_10_ budget_.snastro'; 'con' → 'con_.snastro'; '...' → 'progetto.snastro'; un nome di 80 caratteri → 60 caratteri + '.snastro'
- AC-264 Se <nomeCartella>.snastro esiste già in cartellaGenitore (cartella o file, con la semantica del filesystem: su APFS case-insensitive 'Budget' collide con 'budget'), SessioneProgetto.crea usa '<nomeCartella> (2).snastro', poi ' (3)'… fino al primo nome libero: mai errore, mai sovrascrittura né scrittura dentro la cartella preesistente (il suo contenuto resta byte-identico); la scelta è race-safe (createDirectory atomico, non exists-poi-crea: se il nome viene occupato nel frattempo si passa al numero successivo)
- AC-265 Il Progetto mantiene il nome digitato: CreaProgetto riceve il nome digitato (solo il trim di NomeProgetto, AC-16), mai il nome della cartella; ProgettoAperto.nome, VoceRegistro.nome e ProgettoCreato.nome valgono es. 'Riunione 3/10: budget?' mentre percorso termina con 'Riunione 3_10_ budget_ (2).snastro'; nome vuoto o di soli spazi → NomeProgettoVuoto e nessuna cartella creata
- AC-240 Alla chiusura il registro è aggiornato con numRegistrazioni e ultimaAttivita
- AC-241 Il LettoreAudio ricostruisce un WAV derivato mancante dalla sorgente; senza sorgente riporta non disponibile
- AC-242 Un comando completato produce un Cambiamento su AggiornamentiVista per la sua Registrazione
- AC-315 abbonato-riallineamento-impronte è registrato come AbbonatoDopoCommit sul DispatcherEventi: una Revisione committata porta a un RiallineaImpronte della sua Registrazione (test end-to-end su databaseInMemoria con le Finte ML)
- AC-316 All'apertura del progetto RiallineaTutteLeImpronte gira in background DOPO RecuperaElaborazioniInterrotte, senza bloccare la UI (la schermata è usabile mentre gira)
- AC-317 ImpronteRiallineate(registrazioneId) produce un Cambiamento su AggiornamentiVista per quella Registrazione e invalida la cache della Proposta (nessuna Rigenerazione del Documento)
- AC-329 ServizioModelli (porta di :ui) è implementato in :avvio sopra ProvisioningModelli: ogni variante di snastro.modelli.ErroreModelli è mappata 1:1 nella variante omonima di ErroreServizioModelli (HashNonValido(modelloId), ArchivioNonValido(modelloId), ReteAssente, ScritturaFallita(motivo), DownloadFallito(motivo)) — test a tabella sul mapper; :ui non importa mai snastro.modelli (verificaDipendenzeModuli)

## Dependencies
- Blocks built first: `avvio-coda-elaborazioni` (wave 9), `ui-fondamenta` (wave 6), `lettore-audio` (wave 7), `schermata-progetti` (wave 8), `schermata-registrazioni` (wave 8), `schermata-registrazione` (wave 8), `schermata-parlanti` (wave 8), `schermata-modelli` (wave 8), `persistenza-schema` (wave 2), `registro-progetti-file` (wave 4), `repository-sql-progetto` (wave 4), `repository-sql-trascrizione` (wave 4), `repository-sql-parlanti` (wave 4), `abbonato-documento` (wave 6), `abbonato-registrazione-aggiunta` (wave 5), `abbonato-revisione-parlanti` (wave 5), `abbonato-riallineamento-impronte` (wave 5), `riallinea-impronte` (wave 4), `audio-ffmpeg` (wave 4), `modelli-provisioning` (wave 4)
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
- **eventi-progetto** (consumed/implemented) — owner `eventi-pubblicati`, supplier `crea-progetto, servizi-registrazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ProgettoCreato`: data class(progettoId: ProgettoId, nome: String) : EventoPubblicato
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — SYNC consumer: abbonato-registrazione-aggiunta
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: RegistrazioneAggiunta → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Others → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **eventi-elaborazione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `esegui-elaborazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **eventi-revisione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `revisione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the Trascritto's life (= forever: no re-run after completata)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable forever
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **eventi-parlanti** (consumed/implemented) — owner `eventi-pubblicati`, supplier `conferma-attribuzione, salta-voce, gestione-parlante, riallinea-impronte`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `AttribuzioneConfermata`: data class(voceRef: VoceRef, parlanteId: ParlanteId, precedente: ParlanteId?) : EventoPubblicato
    - `ParlanteCreato`: data class(parlanteId: ParlanteId, progettoId: ProgettoId, nome: String, tipo: TipoParlanteVista) : EventoPubblicato
    - `ParlanteRinominato`: data class(parlanteId: ParlanteId, nome: String) : EventoPubblicato
    - `ParlantePromosso`: data class(parlanteId: ParlanteId, nome: String, nomeCambiato: Boolean) : EventoPubblicato
    - `ParlanteEliminato`: data class(parlanteId: ParlanteId) : EventoPubblicato — NO Documento change
    - `ImpronteRiallineate`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by riallinea-impronte after the commit of >= 1 refreshed print row (ADR 0012 Amendment (b)); consumers: proposta (cache invalidation), avvio-composizione (AggiornamentiVista); NOT Documento (prints do not change it)
    - `TipoParlanteVista`: enum RICORRENTE | OCCASIONALE (parlanti:applicazione)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
- **tec-registro-progetti** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `RegistroProgetti`: interface { elenco(): List<VoceRegistro> /* by ultimaAttivita desc */; registra(v: VoceRegistro); aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) /* keyed by percorso like registra/rimuovi; unknown percorso → no-op */; rimuovi(percorso: String) }
    - `VoceRegistro`: data class(progettoId: ProgettoId, nome: String, percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)
  - keys (minting rules):
    - `percorso`: minted by avvio-composizione (SessioneProgetto crea/apri): absolute path of the <nome>.snastro folder as an opaque string; the registry is keyed by it — a moved folder re-registers on open
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
- **tec-audio-api** (consumed/implemented) — owner `audio-ffmpeg`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.audio.SondaFfmpeg`: fun sonda(file: Path): InfoFile(durataMs: Long, modificatoIl: LocalDate) — throws AudioIlleggibile / FormatoNonSupportato
    - `snastro.audio.DecodificaFfmpeg`: fun decodificaInWav(sorgente: Path, destinazione: Path) — 16 kHz mono 16-bit PCM; fun leggiCampioni(wav: Path, inizioMs: Long, fineMs: Long): FloatArray
    - `snastro.audio.RiproduttoreWav`: AutoCloseable { riproduci(wav: Path, intervalli: List<Pair<Long, Long>>?, daMs: Long); pausa(); posizioneMs(): Long; inRiproduzione(): Boolean }
- **tec-modelli** (consumed/implemented) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)
    - `FormatoVoce`: enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */
    - `snastro.modelli.ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across edits of licence/attribution text, but NEVER reused for different bytes: any change of the entry's sha256 MINTS A NEW id (ADR 0008 Amendment (c)) — the embedding model's id is EstrattoreImpronta.modello, and ADR 0012 (b) staleness (modello_impronta ≠ EstrattoreImpronta.modello) relies on it; also the installed directory name <cartella>/<id>/, whose .sha256 marker records the installed asset hash
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
- **tec-modelli-ui** (consumed/implemented) — owner `schermata-modelli`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ServizioModelli`: interface { val stato: StateFlow<StatoModelli>; fun scarica(); fun licenze(): List<LicenzaVista> }
    - `StatoModelli`: sealed interface { Pronti; Mancanti(numero: Int, totaleByte: Long); InDownload(modelloId: String, scaricatiByte: Long, totaliByte: Long); Errore(errore: ErroreServizioModelli) }
    - `ErroreServizioModelli`: sealed interface : ErroreDominio (file ErroriServizioModelli.kt in snastro.ui.modelli — declared on the UI side because :ui must not depend on :modelli) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — 1:1 image of snastro.modelli.ErroreModelli, mapped in :avvio (avvio-composizione AC-329); same field name modelloId on both sides
    - `LicenzaVista`: data class(nome: String, ruolo: String, licenza: String, attribuzione: String)

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0008, 0010, 0012 (.mismagent/decisions/); architecture.md (:avvio), ADR 0010 (+ R3, R15), profile run binding, user decision 2026-09-23 (folder naming).
