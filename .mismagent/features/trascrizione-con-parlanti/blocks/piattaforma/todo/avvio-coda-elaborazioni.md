---
id: "avvio-coda-elaborazioni"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 9
module: ":avvio"
consumes:
  - "kernel-pl"
  - "tec-segnalatore-fase"
  - "tec-modelli"
depends_on:
  - "esegui-elaborazione"
  - "stati-elaborazione"
  - "modelli-provisioning"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0012"
gated_by:
  - "ADR closing spike attesa-mutex-estrazione"
---
# avvio-coda-elaborazioni — Coda seriale delle Elaborazioni e dispatcher della pipeline

## What to do
Serial FIFO on a single-thread pipeline dispatcher that calls EseguiProssimaElaborazione; holds while ModelliPronti is false; calls RecuperaElaborazioniInterrotte before starting, after anything escapes esegui and on project re-open; does not spin on a head item whose start keeps failing; wires FasiInCorso as SegnalatoreFase; one Mutex serializes every native use (pipeline + EstrattoreImpronta), always acquired outside any transaction (ADR 0012 (b) point 5).

Note: PINNED REQUIREMENT (esegui-elaborazione cycle-1 code-review F-G): the start in_attesa → in_corso relies on the SINGLE-THREAD dispatcher (no version check / compare-and-set on that transition) — never run two dispatchers. Carry-over F-C: after anything escapes esegui no run is live, so RecuperaElaborazioniInterrotte runs before the next item and on project re-open. OPEN — DEFERRED [user] (ADR 0012 Amendment (b) Consequences): a Conferma/SaltaVoce/Proposta requested during an Elaborazione still waits for the native Mutex (potentially minutes); it no longer blocks the DB, but the user-facing wait is unresolved (candidates: Mutex timeout with 'riprova dopo l'elaborazione', per-call release between pipeline chunks, separate extractor session) — to be DECIDED BEFORE this block is built. GATE 2026-09-23 (user decision): the Mutex-wait question is now the spike node tasks/app/backlog/attesa-mutex-estrazione.md (separate small ONNX session for the print extractor vs pipeline releasing the lock between chunks vs UI 'occupato' state); this block is NOT READY until the ADR closing it lands and build-manifest folds the decision into its tests_nl.

## Tasks
- AC-233 All'avvio RecuperaElaborazioniInterrotte gira prima che la coda parta
- AC-234 Con due in_attesa la seconda parte solo quando la prima è terminale
- AC-235 Con i modelli mancanti le Elaborazioni restano in_attesa; quando diventano pronti la coda riparte
- AC-236 Un'estrazione d'impronta richiesta dalla UI durante un'Elaborazione attende il Mutex nativo senza alcuna transazione aperta (nessuna chiamata nativa concorrente; il write lock di SQLite non è tenuto durante l'attesa)
- AC-312 (F-C) Se qualcosa sfugge a EseguiProssimaElaborazione.esegui (cancellazione, interrupt, Error come OutOfMemoryError, eccezione inattesa) il dispatcher esegue RecuperaElaborazioniInterrotte prima di prendere l'elemento successivo; lo esegue anche quando un progetto è riaperto senza riavviare l'app — nessuna Elaborazione resta in_corso mentre l'app gira
- AC-313 (F-G) Un abbonato sincrono che fallisce sempre su ElaborazioneAvviata annulla ogni volta la transazione di avvio: il dispatcher non gira a vuoto sull'elemento di testa (tentativi limitati con back-off, poi lo lascia in_attesa per la sessione e lo segnala) e gli altri elementi proseguono
- AC-314 Un solo dispatcher single-thread prende gli elementi: due richieste concorrenti di avanzamento non portano mai due Elaborazioni in_corso (la transizione in_attesa → in_corso non ha controllo di versione e si affida a questo)

## Dependencies
- **GATED — not ready until:** ADR closing spike attesa-mutex-estrazione
- Blocks built first: `esegui-elaborazione` (wave 4), `stati-elaborazione` (wave 5), `modelli-provisioning` (wave 4)
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
- **tec-segnalatore-fase** (consumed/implemented) — owner `porte-trascrizione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SegnalatoreFase`: interface { fun fase(id: RegistrazioneId, f: FaseElaborazione); fun terminata(id: RegistrazioneId) }
    - `FaseElaborazione`: enum DECODIFICA | DIARIZZAZIONE | TRASCRIZIONE | ALLINEAMENTO (trascrizione:applicazione) — progress only, not guarded state
- **tec-modelli** (consumed/implemented) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)
    - `FormatoVoce`: enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */
    - `snastro.modelli.ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across edits of licence/attribution text, but NEVER reused for different bytes: any change of the entry's sha256 MINTS A NEW id (ADR 0008 Amendment (c)) — the embedding model's id is EstrattoreImpronta.modello, and ADR 0012 (b) staleness (modello_impronta ≠ EstrattoreImpronta.modello) relies on it; also the installed directory name <cartella>/<id>/, whose .sha256 marker records the installed asset hash

Sources: ADRs 0002, 0003, 0004, 0008, 0012 (.mismagent/decisions/); ADR 0004/0008/0012 (+ R12).
