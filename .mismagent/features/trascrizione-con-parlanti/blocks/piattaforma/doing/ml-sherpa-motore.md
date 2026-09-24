---
id: "ml-sherpa-motore"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 11
release: "R1"
module: ":ml-sherpa + root build.gradle.kts (tasks scaricaJarSherpa, scaricaNativiSherpa, modelliTest edge) + avvio/build.gradle.kts (only nativeDistributions.appResourcesRootDir and task edges) + gradle/libs.versions.toml (sherpa-onnx version)"
consumes:
  - "kernel-pl"
  - "tec-modelli"
depends_on: []
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0008"
  - "0012"
  - "0016"
  - "0017"
gated_by:
  - "ADR closing spike packaging-modelli-desktop — satisfied: ADR 0016 (accepted 2026-09-24)"
owns_boundaries:
  tec-ml-sherpa:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      "snastro.ml.MotoreSherpa": "fun caricaNativi(); fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T — AutoCloseable released after use; holds ONE process-wide FAIR Mutex for one native session (ONE native call at a time); the wait is INTERRUPTIBLE: an interrupt while waiting → InterruptedException, and no session, no native load and no uso run; not reentrant; the Mutex is released on return or exception; every adapter holds it for ONE port call only (ADR 0017 §1)"
      ConfigSessione: "data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = \"cpu\")"
---
# ml-sherpa-motore — Motore sherpa-onnx: nativi, sessioni, wrapper

## What to do
Own the pinned sherpa-onnx 1.13.8 fetch (ADR 0016): scaricaJarSherpa (jar, SHA-256, native-cache/) and scaricaNativiSherpa (osx-arm64 JNI tarball, SHA-256, only the two dylibs into :avvio's appResourcesRootDir/macos-arm64/), wired to run/distribution/modelliTest but never to check; MotoreSherpa.caricaNativi() explicit, idempotent native load (sherpa_onnx.native.path, else compose.application.resources.dir); ConfigSessione (CPU default, intra-op = P-cores), AutoCloseable session wrappers, the native Mutex serializing conSessione.

Note: AMENDED 2026-09-23 (ADR 0008 Amendment (c)): ProvisioningModelli.percorso(id) returns the installed DIRECTORY <cartella>/<id>/ (archive extracted, single top-level dir stripped); the adapters resolve their file names (encoder/decoder/joiner/tokens, model.onnx) inside it — the file names come from the spike ADR that adds the catalogue entry. AMENDED 2026-09-24 (ADR 0016, manifest delta 2026-09-24-packaging): this block OWNS the pinned sherpa-onnx 1.13.8 fetch (jar + osx-arm64 JNI natives, SHA-256 verified, native-cache/ gitignored, surviving clean) that the wave-0 scaffold-app stub left to it, and the explicit native load in MotoreSherpa.caricaNativi(). The gate needs network access ONCE, for the jar; natives are never downloaded by check. It edits avvio/build.gradle.kts only for appResourcesRootDir and task edges — avvio-composizione (wave 10) is built before it, so no collision. ADR 0016 enforced_by (no native/sherpa artifact tracked by git) is checked by the verifier. NOT folded (pending the user): the optional benchmark CoreML AC of the packaging delta. AMENDED 2026-09-24 (reality, merge fbd6e46; ADR 0016 Amendment 2026-09-24): AC-397 now records LibraryLoader.setAutoLoadEnabled(false) before LibraryUtils.load(), as built. ADR 0017: AC-401 unchanged; the fair, interruptible wait on the same Mutex is carried by estrattore-impronta-sherpa (AC-408/409).

## Tasks
- AC-243 (REWRITTEN 2026-09-24, ADR 0016) [@modelli] i nativi si caricano su macOS arm64 da ./gradlew :avvio:run (compose.application.resources.dir) e da ./gradlew modelliTest (sherpa_onnx.native.path impostato dal task di test a <appResourcesRootDir>/macos-arm64/)
- AC-244 [@modelli] ogni sessione è rilasciata dopo l'uso (nessuna handle nativa sopravvive a conSessione)
- AC-245 com.k2fsa e System.load compaiono solo in :ml-sherpa (regola enforced_by di ADR 0004)
- AC-390 gradle/libs.versions.toml dichiara sherpa-onnx = "1.13.8"; URL e SHA-256 del jar (77b7b047…a63b) e di osx-arm64-jni.tar.bz2 (2505fd9b…31ad) compaiono una sola volta negli script di build, uguali ad ADR 0016 §1
- AC-391 scaricaJarSherpa scarica sherpa-onnx-jvm-1.13.8.jar in native-cache/sherpa-onnx-1.13.8/ e ne verifica lo SHA-256; :ml-sherpa compila contro di esso via files(...).builtBy(scaricaJarSherpa); la cache sopravvive a ./gradlew clean
- AC-392 scaricaNativiSherpa scarica osx-arm64-jni.tar.bz2, ne verifica lo SHA-256 ed estrae SOLO libonnxruntime.dylib e libsherpa-onnx-jni.dylib, piatti (senza prefisso lib/), in appResourcesRootDir/macos-arm64/ di :avvio (directory generata sotto avvio/build/); nessun altro membro dell'archivio è estratto
- AC-393 SHA-256 non corrispondente (prova negativa usa-e-getta durante il blocco con un hash atteso sbagliato, non committata) → il task fallisce, il file scaricato è cancellato e nulla finisce sul classpath né in appResourcesRootDir
- AC-394 Una seconda esecuzione con i file presenti è UP-TO-DATE e non fa chiamate di rete (input e output dichiarati)
- AC-395 :avvio:run, :avvio:createDistributable, :avvio:prepareAppResources e modelliTest dipendono da scaricaNativiSherpa; check NO: ./gradlew check --dry-run non elenca scaricaNativiSherpa, ./gradlew :avvio:run --dry-run lo elenca; il gate resta verde con native-cache/ svuotata delle dylib
- AC-396 Su un host os-arch senza asset fissato (tutto tranne macOS arm64 in v1) scaricaNativiSherpa fallisce con un messaggio che nomina l'asset mancante, mai un salto silenzioso (verificato in code review; i nomi Windows/Linux sono documentati in ADR 0016 §5 e non cablati)
- AC-397 MotoreSherpa.caricaNativi() risolve la directory in quest'ordine: sherpa_onnx.native.path se già impostata, altrimenti compose.application.resources.dir; poi imposta sherpa_onnx.native.path, chiama LibraryLoader.setAutoLoadEnabled(false) (nessun caricamento automatico di ripiego dei costruttori sherpa: risorsa nel jar, java.library.path — ADR 0016 Amendment 2026-09-24) e poi LibraryUtils.load(); java.library.path non è mai letta né impostata. Nel gate: senza nessuna delle due proprietà, o con una directory priva delle due librerie, caricaNativi fallisce con un messaggio che nomina entrambe le proprietà e LibraryUtils.load() non è invocata
- AC-398 [@modelli] caricaNativi() è idempotente: due chiamate, o due conSessione, caricano i nativi una sola volta, senza errori
- AC-399 conSessione chiama caricaNativi() in modo pigro, prima della creazione del primo oggetto sherpa; nessun codice di :avvio né alcun adattatore consumatore chiama LibraryUtils o System.load (ADR 0004 enforced_by, AC-245); nessun test del gate carica i nativi
- AC-400 ConfigSessione.provider vale "cpu" per default; R1 non espone alcuna impostazione utente per un altro provider (ADR 0016 §6)
- AC-401 conSessione serializza: due conSessione concorrenti non si sovrappongono mai (la seconda attende il Mutex nativo) e il Mutex è rilasciato anche su eccezione — parte R1 del contratto del Mutex, con un solo detentore (la pipeline); ADR 0017 mantiene questo contratto e aggiunge un'attesa equa e interrompibile (portata da estrattore-impronta-sherpa, AC-408/409); testabile nel gate se la factory della sessione è sostituibile, altrimenti [@modelli]

## Dependencies
- **GATED — not ready until:** ADR closing spike packaging-modelli-desktop — satisfied: ADR 0016 (accepted 2026-09-24)
- **tec-ml-sherpa** (OWNED here — built before its consumers) — owner `ml-sherpa-motore`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.ml.MotoreSherpa`: fun caricaNativi(); fun <T> conSessione(config: ConfigSessione, uso: (SessioneSherpa) -> T): T — AutoCloseable released after use; holds ONE process-wide FAIR Mutex for one native session (ONE native call at a time); the wait is INTERRUPTIBLE: an interrupt while waiting → InterruptedException, and no session, no native load and no uso run; not reentrant; the Mutex is released on return or exception; every adapter holds it for ONE port call only (ADR 0017 §1)
    - `ConfigSessione`: data class(percorsiModello: List<Path>, threadIntraOp: Int, provider: String = "cpu")
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
- **tec-modelli** (consumed/implemented) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)
    - `FormatoVoce`: enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */
    - `snastro.modelli.ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across edits of licence/attribution text, but NEVER reused for different bytes: any change of the entry's sha256 MINTS A NEW id (ADR 0008 Amendment (c)) — the embedding model's id is EstrattoreImpronta.modello, and ADR 0012 (b) staleness (modello_impronta ≠ EstrattoreImpronta.modello) relies on it; also the installed directory name <cartella>/<id>/, whose .sha256 marker records the installed asset hash

Sources: ADRs 0002, 0003, 0004, 0008, 0012, 0016, 0017 (.mismagent/decisions/); ADR 0004, ADR 0016 (§1 pinned artifacts, §2-§6), spike packaging-modelli-desktop, research/spike-packaging-modelli-desktop.md.
