---
id: "avvio-composizione"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 10
release: "R1"
module: ":avvio"
consumes:
  - "kernel-pl"
  - "eventi-progetto"
  - "eventi-elaborazione"
  - "eventi-revisione"
  - "nomi-per-documento"
  - "tec-modelli"
  - "tec-shell-ui"
  - "tec-modelli-ui"
depends_on:
  - "avvio-r0"
  - "avvio-coda-elaborazioni"
  - "schermata-registrazione"
  - "schermata-modelli"
  - "repository-sql-trascrizione"
  - "registrazione-da-progetto-tr"
  - "decodifica-trascrizione"
  - "lettore-trascritto-da-trascrizione"
  - "scrittore-documento-md"
  - "abbonato-documento"
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
  - "0014"
---
# avvio-composizione — Composizione R1 (Trascrizione): coda, pipeline, Revisione, Documento, Modelli, S3/S5

## What to do
R1 composition (release Trascrizione): EXTENDS avvio-r0's graph (never re-creates SessioneProgetto, the dispatcher or LettoreAudio) with the Trascrizione SQL repositories, the serial Elaborazione queue, pipeline adapters with config-driven selection (Finte until the ML blocks wire themselves here), Revisione commands (no UI in R1), the Documento after-commit regeneration with an empty LettoreNomi ('Voce n'), ServizioModelli over :modelli (errors mapped, exceptions caught), the Trascrizione sources for S2, S3 read-only (no Parlanti sources, no Revisione commands) and S5, FasiInCorso as one shared instance, and the --smoke extension for S3/S5.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): this block is now the R1 (Trascrizione) half of the former monolithic composition root; the R0 half is avvio-r0 (SessioneProgetto, folder naming AC-239/263/264/265, registry AC-240/347/348, LettoreAudio AC-241, AggiornamentiVista AC-242, smoke S1/S2 AC-237, 'progetto già aperto' AC-238) and the R2 half is avvio-parlanti (AC-315/316/317). It EXTENDS avvio-r0's graph (never re-creates SessioneProgetto, the dispatcher or LettoreAudio): SQL repositories of Trascrizione, the serial queue (avvio-coda-elaborazioni), pipeline adapters with config-driven selection (Finte until the gated ML blocks land — diarizzatore/riconoscitore/vad wire themselves here), Revisione commands, Documento (rigenerazione after commit, empty LettoreNomi AC-356), ServizioModelli over :modelli (AC-329, exceptions caught AC-352), S3/S5. Carry-overs owned here: eventi.unitaDiLavoro wiring for every Trascrizione command (revisione code-review); FasiInCorso ONE shared instance for pipeline + read-model (stati-elaborazione, AC-353) and phase changes emitting a Cambiamento (stati-elaborazione code-review, AC-354); ServizioModelli must catch the IOExceptions escaping scarica()/pronti() (modelli-provisioning cycle-1 code-review, AC-352); RecuperaElaborazioniInterrotte strictly before the dispatcher (owned by avvio-coda-elaborazioni AC-233). AMENDED 2026-09-24 (ADR 0014 / ADR 0012 Amendment (c)): abbonato-registrazione-aggiunta is REMOVED — the composition registers no sync subscriber on RegistrazioneAggiunta; AvviaElaborazione is reached only from S2 ('Trascrivi'/'Riprova' with the optional Numero di persone). tec-modelli percorso(id) is a DIRECTORY (ADR 0008 Amendment (c)); tec-modelli-ui error type is the UI-side ErroreServizioModelli (AC-329). AMENDED 2026-09-24 (delta 2026-09-24-packaging, user decisions 2026-09-24): S3 is built READ-ONLY in R1 (variant A) — the composition supplies it trascritto-view, documento, LettoreAudio and ApriEsterno only (no Parlanti sources, no Revisione commands: explicit cut, the Revisione UI arrives in R2 with schermata-registrazione-identificazione). The Revisione services and their events stay wired (AC-354/AC-356 are proved end-to-end by tests, not from the UI). avvio-coda-elaborazioni is no longer gated (Mutex gate scoped to R2), so this block is buildable once its depends_on are merged. The real ML adapters (ml-sherpa-motore wave 11, the three sherpa adapters wave 12) are built after it and wire themselves here; until then the pipeline runs on the Finte.

## Tasks
- AC-351 --smoke (esteso) salva anche gli screenshot di S3 (progetto fixture con un Trascritto completato, etichette 'Voce n') e di S5 in avvio/build/smoke/, uscendo con 0; S1/S2 restano come in AC-237
- AC-329 ServizioModelli (porta di :ui) è implementato in :avvio sopra ProvisioningModelli: ogni variante di snastro.modelli.ErroreModelli è mappata 1:1 nella variante omonima di ErroreServizioModelli (HashNonValido(modelloId), ArchivioNonValido(modelloId), ReteAssente, ScritturaFallita(motivo), DownloadFallito(motivo)) — test a tabella sul mapper; :ui non importa mai snastro.modelli (verificaDipendenzeModuli)
- AC-352 Un'eccezione che sfugge a ProvisioningModelli (IOException o InvalidPathException da scarica() o pronti()) è catturata in :avvio: scarica → StatoModelli.Errore(ScritturaFallita(motivo)), mai un crash né uno stato bloccato in InDownload, e 'Riprova' la ripete; pronti() che lancia all'avvio → Mancanti (l'app parte comunque) — test con un ProvisioningModelli finto che lancia
- AC-353 Una sola istanza di FasiInCorso per progetto aperto è passata sia alla pipeline (SegnalatoreFase di EseguiProssimaElaborazione) sia a StatiElaborazione: una fase segnalata dalla pipeline è visibile nella vista di S2 (test end-to-end con le Finte ML)
- AC-354 ElaborazioneAvviata/Completata/Fallita, VociUnite, VoceDivisa, SegmentoRiassegnato e OGNI cambio di fase di FasiInCorso producono un Cambiamento su AggiornamentiVista per la loro Registrazione (così S2 aggiorna stato e fase, AC-205, senza polling)
- AC-355 La composizione R1 fornisce a S2 le sorgenti di Trascrizione (AC-342 → colonna di stato, campo 'Numero di persone' + 'Trascrivi'/'Riprova', apertura di S3 su completata) e rende raggiungibile S5; NESSUN abbonato sincrono è registrato su RegistrazioneAggiunta (nessun avvio automatico, ADR 0014) e abbonato-documento è registrato come AbbonatoDopoCommit; ogni servizio di comando della Trascrizione usa eventi.unitaDiLavoro (come AC-346); la sezione Parlanti resta assente (AC-341) — REWRITTEN 2026-09-24 (ADR 0014)
- AC-371 (ex AC-NP5, e2e) In R1 AggiungiRegistrazione non crea alcuna Elaborazione: dopo un'importazione nella composizione R1 la tabella elaborazione non ha righe per quella Registrazione, la vista StatiElaborazione la dà NON_AVVIATA e S2 mostra 'Trascrivi' (test end-to-end con la composizione R1 e le Finte ML)
- AC-356 Senza Parlanti (R1) il Documento è generato con un LettoreNomi vuoto (nomi = mappa vuota, registrazioniCon = lista vuota): ogni Voce è resa 'Voce n' (INV-24) e una Revisione committata rigenera il Documento; nessuna classe di :parlanti è istanziata dalla composizione R1

## Dependencies
- Blocks built first: `avvio-r0` (wave 9), `avvio-coda-elaborazioni` (wave 9), `schermata-registrazione` (wave 8), `schermata-modelli` (wave 8), `repository-sql-trascrizione` (wave 4), `registrazione-da-progetto-tr` (wave 5), `decodifica-trascrizione` (wave 5), `lettore-trascritto-da-trascrizione` (wave 5), `scrittore-documento-md` (wave 5), `abbonato-documento` (wave 6), `modelli-provisioning` (wave 4)
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
    - `RegistrazioneAggiunta`: data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId) : EventoPubblicato — AFTER-COMMIT consumers only (view refresh); NO synchronous subscriber (no automatic start on import, ADR 0014 / ADR 0012 Amendment (c))
    - `DataRegistrazioneModificata`: data class(registrazioneId: RegistrazioneId, precedente: LocalDate, nuova: LocalDate) : EventoPubblicato — AFTER-COMMIT consumer: abbonato-documento
  - keys (minting rules):
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: All three events → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. AMENDED 2026-09-24 (ADR 0014 / ADR 0012 Amendment (c)): the SYNCHRONOUS clause for RegistrazioneAggiunta is dropped — it has no sync subscriber (the dispatcher's sync mechanism itself is unchanged, ADR 0012)
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
- **nomi-per-documento** (consumed/implemented) — owner `porta-lettore-nomi`, supplier `nomi-delle-voci`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreNomi`: interface { fun nomi(id: RegistrazioneId): Map<VoceRef, String>; fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> } — attributed Voci only; an eliminato Parlante still resolves to its Nome (INV-24)
  - keys (minting rules):
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
- **tec-modelli** (consumed/implemented) — owner `modelli-provisioning`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `snastro.modelli.CatalogoModelli`: val voci: List<VoceCatalogo>
    - `VoceCatalogo`: data class(id: String, ruolo: String, url: String, sha256: String /* of the downloaded ASSET (the archive, or the single file) */, dimensioneByte: Long /* of the asset */, formato: FormatoVoce, licenza: String, attribuzione: String) — ADR 0008 Amendment (c)
    - `FormatoVoce`: enum class { TAR_BZ2 /* k2-fsa .tar.bz2 release: extracted, a single top-level directory stripped */, FILE /* single asset, placed as <id>/<file name of the url> */ }
    - `snastro.modelli.ProvisioningModelli`: fun pronti(): Boolean; fun mancanti(): List<VoceCatalogo>; fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit>; fun percorso(id: String): Path /* the installed DIRECTORY <cartella>/<id>/ — never a file */
    - `snastro.modelli.ErroreModelli`: sealed interface : ErroreDominio (file ErroriModelli.kt) { HashNonValido(modelloId: String); ArchivioNonValido(modelloId: String, motivo: String); ReteAssente; ScritturaFallita(motivo: String); DownloadFallito(motivo: String) } — declared in :modelli, NEVER referenced by :ui (avvio-composizione maps it to the :ui ErroreServizioModelli)
  - keys (minting rules):
    - `VoceCatalogo.id`: minted by the spike ADR that chooses the model (e.g. 'segmentazione-pyannote-3.0'); stable across edits of licence/attribution text, but NEVER reused for different bytes: any change of the entry's sha256 MINTS A NEW id (ADR 0008 Amendment (c)) — the embedding model's id is EstrattoreImpronta.modello, and ADR 0012 (b) staleness (modello_impronta ≠ EstrattoreImpronta.modello) relies on it; also the installed directory name <cartella>/<id>/, whose .sha256 marker records the installed asset hash
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

Sources: ADRs 0002, 0003, 0004, 0005, 0006, 0008, 0010, 0012, 0014 (.mismagent/decisions/); architecture.md (:avvio), ADR 0004/0008/0010/0012, release pivot 2026-09-23 (R1 Trascrizione).
