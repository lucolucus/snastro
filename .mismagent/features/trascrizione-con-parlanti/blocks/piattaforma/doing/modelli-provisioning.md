---
id: "modelli-provisioning"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 4
module: ":modelli"
consumes:
  - "kernel-pl"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0008"
  - "0012"
owns_boundaries:
  tec-modelli:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      "snastro.modelli.CatalogoModelli": "val voci: List<VoceCatalogo>"
      VoceCatalogo: "data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)"
      FormatoVoce: "enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }"
      "snastro.modelli.ProvisioningModelli": "fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */"
      "snastro.modelli.ErroreModelli": "sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)"
---
# modelli-provisioning — Catalogo e download dei modelli

## What to do
Model catalogue (entries added by the spike ADRs; each entry an ARCHIVE .tar.bz2 or a single FILE), first-run download with progress, resume, redirects and timeouts, SHA-256 verify of the asset, extraction into a temp directory next to the target and atomic directory rename, <id>/.sha256 marker, per-OS models dir (%LOCALAPPDATA% on Windows), pronti()/mancanti(); percorso(id) = the installed directory. The ONLY network module.

Note: AMENDED 2026-09-23 (ADR 0008 Amendment (c), user decisions: %LOCALAPPDATA% + ARCHIVE entries): VoceCatalogo describes the downloaded asset (url, sha256 and dimensioneByte OF THE ASSET, formato TAR_BZ2 | FILE); verify → extract into a temp dir next to the target → atomic dir rename; percorso(id) = the directory; <id>/.sha256 marker; blank/relative LOCALAPPDATA / XDG_DATA_HOME ignored. Extraction via Apache Commons Compress (org.apache.commons:commons-compress, Apache-2.0, add to gradle/libs.versions.toml; :modelli only; BZip2CompressorInputStream + TarArchiveInputStream) — the JDK has no bzip2/tar reader. HashNonValido field renamed id → modelloId (reconciled with tec-modelli-ui); ErroreModelli gains ArchivioNonValido + ScritturaFallita. FOLLOW-UP REQUIRED: the queued code-review CHANGES rework (cycle 1) must cover AC-129/130/133 (amended) and AC-330..339 — incl. the three HIGH items (AC-336 redirects, AC-337 stuck .part, AC-338 inactivity timeout) — plus the review's MED patches (unguarded file ops, body stream leak, concurrent scarica, M8 early-EOF test).

## Tasks
- AC-129 Un asset (archivio o file) con hash errato → HashNonValido(modelloId), il .part è eliminato (mai riusato) e nessuna directory è installata nella cache
- AC-130 Il download scrive in <id>.part; solo dopo la verifica dello sha256 l'asset è estratto/copiato in una directory temporanea accanto alla destinazione (<cartella>/<id>.tmp-<n>/) che è rinominata atomicamente in <cartella>/<id>/
- AC-131 Un download interrotto riprende dal punto raggiunto
- AC-132 Rete assente → ReteAssente; pronti() è false finché manca un modello del catalogo
- AC-133 La cartella dei modelli segue il sistema operativo (funzione pura con os, env e user.home iniettati): macOS ~/Library/Application Support/snastro/modelli; Windows %LOCALAPPDATA%\snastro\modelli (mai %APPDATA%, roaming); Linux $XDG_DATA_HOME/snastro/modelli (mai XDG_CACHE_HOME); non è mai nel progetto
- AC-134 I test usano una sorgente locale (nessuna rete reale nel gate)
- AC-330 Voce TAR_BZ2: un archivio .tar.bz2 costruito nel test con top/encoder.onnx e top/tokens.txt, servito dal server finto su loopback, è verificato ed estratto in <cartella>/<id>/encoder.onnx e <id>/tokens.txt (la directory di primo livello unica è rimossa); percorso(id) = <cartella>/<id>/ (una DIRECTORY); dopo l'installazione il .part non esiste più
- AC-331 Voce FILE: il file verificato è posto in <cartella>/<id>/<nome del file dell'url>; percorso(id) è comunque la directory
- AC-332 Un archivio con una voce che esce dalla destinazione ('../x', percorso assoluto) o con un link simbolico/fisico → ArchivioNonValido(modelloId), nulla è rinominato al posto della destinazione e nessuna directory temporanea resta
- AC-333 Marcatore <id>/.sha256 = hash dell'asset installato: installata(id) solo se la directory esiste E il marcatore coincide con lo sha256 del catalogo; marcatore diverso (hash del catalogo cambiato) o assente → la voce è in mancanti() e scarica la reinstalla sostituendo la vecchia directory (rinominata da parte, poi eliminata); le directory *.tmp-* rimaste da un crash sono eliminate all'avvio
- AC-334 Windows: LOCALAPPDATA assoluto → <LOCALAPPDATA>\snastro\modelli; LOCALAPPDATA assente, vuoto/solo spazi o RELATIVO → <user.home>\AppData\Local\snastro\modelli
- AC-335 Linux: XDG_DATA_HOME assoluto → <XDG_DATA_HOME>/snastro/modelli; assente, vuoto/solo spazi o RELATIVO → <user.home>/.local/share/snastro/modelli (spec XDG)
- AC-336 (code-review HIGH) I redirect HTTP sono seguiti: il server finto risponde 302 verso un secondo percorso che serve l'asset (come i release di GitHub) → download completato e verificato; un ciclo di redirect → DownloadFallito, mai un blocco
- AC-337 (code-review HIGH) Un .part bloccato si recupera: una richiesta Range a cui il server risponde 416, o un .part più grande di dimensioneByte, → il .part è scartato e il download riparte da 0 (una volta) e riesce; un upstream corto (la connessione si chiude prima di dimensioneByte / Content-Length) → DownloadFallito con il .part conservato, e lo scarica successivo riprende e completa
- AC-338 (code-review HIGH) Timeout di lettura/inattività: un server che smette di inviare byte a metà corpo → il download fallisce entro il timeout di inattività (iniettato, es. 200 ms nel test) con DownloadFallito, mai un blocco indefinito; il .part resta per la ripresa; anche la connessione ha un timeout
- AC-339 Un errore del disco (cartella non scrivibile, rinomina fallita) → ScritturaFallita(motivo), mai ReteAssente

## Dependencies
- **tec-modelli** (OWNED here — built before its consumers) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)
    - `FormatoVoce`: enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */
    - `snastro.modelli.ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across edits of licence/attribution text, but NEVER reused for different bytes: any change of the entry's sha256 MINTS A NEW id (ADR 0008 Amendment (c)) — the embedding model's id is EstrattoreImpronta.modello, and ADR 0012 (b) staleness (modello_impronta ≠ EstrattoreImpronta.modello) relies on it; also the installed directory name <cartella>/<id>/, whose .sha256 marker records the installed asset hash
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

Sources: ADRs 0002, 0003, 0008, 0012 (.mismagent/decisions/); ADR 0008.
