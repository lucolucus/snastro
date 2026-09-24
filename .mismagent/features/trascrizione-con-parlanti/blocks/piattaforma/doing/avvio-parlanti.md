---
id: "avvio-parlanti"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 11
release: "R2"
module: ":avvio"
consumes:
  - "kernel-pl"
  - "eventi-revisione"
  - "eventi-parlanti"
  - "tec-shell-ui"
depends_on:
  - "avvio-composizione"
  - "schermata-parlanti"
  - "schermata-registrazioni-identificazione"
  - "schermata-registrazione-identificazione"
  - "repository-sql-parlanti"
  - "registrazione-da-progetto-pa"
  - "lettore-voci-da-trascrizione"
  - "lettore-nomi-da-parlanti"
  - "decodifica-parlanti"
  - "confronto-impronte"
  - "abbonato-revisione-parlanti"
  - "abbonato-riallineamento-impronte"
  - "riallinea-impronte"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0009"
  - "0010"
  - "0012"
  - "0017"
gated_by:
  - "ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)"
---
# avvio-parlanti — Composizione R2 (Parlanti): repository, abbonati, riallineamento impronte, S4, badge S2, pannello S3

## What to do
R2 composition (release Parlanti, PAUSED): EXTENDS avvio-composizione's graph with the Parlanti SQL repositories and adapters, the revisione-policy sync subscriber, abbonato-revisione-parlanti and abbonato-riallineamento-impronte after commit, RiallineaTutteLeImpronte in background at project open (after RecuperaElaborazioniInterrotte, exceptions caught and logged, cancelled on close), lettore-nomi-da-parlanti replacing the empty LettoreNomi, the Parlanti shell section (S4), the S2 identification badge, the S3 Voci panel + Revisione UI (Parlanti sources and commands supplied to the S3 presenter), the native-Mutex wait of print extraction (AC-236), and the --smoke extension for S4.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): R2 (Parlanti — PAUSED) half of the former monolithic composition root; EXTENDS avvio-composizione's graph. ADR 0012 Amendment (b): wire abbonato-riallineamento-impronte (after commit) and run RiallineaTutteLeImpronte at project open, in background, after RecuperaElaborazioniInterrotte; pass the same UnitaDiLavoro to the Parlanti commands and to RiallineaImpronte. Carry-over owned here: catch/log RiallineaTutteLeImpronte exceptions, never crash the background scope, cancel on project close (riallinea-impronte code-review F2, AC-358). The real EstrattoreImpronta (estrattore-impronta-sherpa) wires itself here. AMENDED 2026-09-24 (delta 2026-09-24-packaging, user decisions 2026-09-24): takes the attesa-mutex-estrazione gate moved from R1 (AC-236 moved here from avvio-coda-elaborazioni); supplies the Parlanti sources and the Revisione/identification commands to the S3 presenter by wiring schermata-registrazione-identificazione (the S3 identification panel + Revisione UI, cut from R1). AMENDED 2026-09-24 (ADR 0017, manifest delta 2026-09-24-mutex): gate attesa-mutex-estrazione SATISFIED; AC-236 rewritten with the chosen behaviour; AC-418..AC-421 (per-project command scope + per-VoceRef pending state and annulla for schermata-registrazione-identificazione AC-415, cancellation writes nothing, close joins pending work before the DB closes, one screen-scoped Proposta job).

## Tasks
- AC-357 --smoke (esteso) salva anche lo screenshot di S4 e quello di S2 con il badge di identificazione, uscendo con 0; la shell mostra la sezione Parlanti (AC-341 con sezione fornita, AC-177)
- AC-315 abbonato-riallineamento-impronte è registrato come AbbonatoDopoCommit sul DispatcherEventi: una Revisione committata porta a un RiallineaImpronte della sua Registrazione (test end-to-end su databaseInMemoria con le Finte ML)
- AC-316 All'apertura del progetto RiallineaTutteLeImpronte gira in background DOPO RecuperaElaborazioniInterrotte, senza bloccare la UI (la schermata è usabile mentre gira)
- AC-317 ImpronteRiallineate(registrazioneId) produce un Cambiamento su AggiornamentiVista per quella Registrazione e invalida la cache della Proposta (nessuna Rigenerazione del Documento)
- AC-358 Un'eccezione di RiallineaTutteLeImpronte (all'apertura) o di RiallineaImpronte (abbonato dopo-commit) è catturata e registrata nel log: non termina lo scope di background né l'app, gli altri abbonati continuano; il job è cancellato alla chiusura del progetto (nessun accesso al DB dopo chiudi) — test con una finta che lancia
- AC-359 La composizione R2 registra revisione-policy come AbbonatoSincrono agli eventi di Revisione (una sua Errore annulla la Revisione) e sostituisce il LettoreNomi vuoto di R1 con lettore-nomi-da-parlanti (il Documento mostra i Nomi attribuiti); i comandi Parlanti e RiallineaImpronte ricevono la STESSA UnitaDiLavoro eventi.unitaDiLavoro
- AC-236 (REWRITTEN 2026-09-24, ADR 0017) Un'estrazione d'impronta richiesta durante un'Elaborazione attende il Mutex nativo SENZA transazione aperta (il write lock di SQLite non è tenuto), su un dispatcher di background tramite runInterruptible, mai sul thread UI; attende al più la chiamata nativa in corso al momento della richiesta più le estrazioni accodate prima (Mutex equo, ADR 0017 §1.3). Test end-to-end su databaseInMemoria: un EstrattoreImpronta finto bloccabile condivide un ReentrantLock(true) con un passo finto della pipeline che lo tiene; il flusso di stato della UI continua a emettere e il comando completa dopo il rilascio
- AC-418 (ADR 0017 §3) ConfermaAttribuzione / SaltaVoce da S3 girano in uno scope per progetto, non in quello della schermata, con uno stato in corso per VoceRef esposto al presenter di S3 e un annulla(voceRef); uscire da S3 non li annulla
- AC-419 annulla(voceRef) durante l'attesa del Mutex → InterruptedException/cancellazione, nulla scritto (nessuna Attribuzione, nessuna riga d'impronta, nessun Parlante, nessun evento) e nessun errore mostrato come fallimento — test: conteggi delle righe invariati
- AC-420 Chiudere il progetto annulla ogni comando Parlanti e ogni job di Proposta in corso; la chiusura (ferma) li attende, con un limite come CollaboratoriR1.ferma, prima della chiusura del database; nulla è scritto dopo chiudi — test: una finta trattenuta + chiudi, nessun accesso al DB dopo la chiusura
- AC-421 Le Proposte della Registrazione aperta sono calcolate una Voce alla volta in UN solo job di background legato alla schermata; uscire da S3 o cambiare Registrazione lo annulla; mai N attese concorrenti sul Mutex per un pannello

## Dependencies
- **GATED — not ready until:** ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)
- Blocks built first: `avvio-composizione` (wave 10), `schermata-parlanti` (wave 8), `schermata-registrazioni-identificazione` (wave 9), `schermata-registrazione-identificazione` (wave 9), `repository-sql-parlanti` (wave 4), `registrazione-da-progetto-pa` (wave 5), `lettore-voci-da-trascrizione` (wave 5), `lettore-nomi-da-parlanti` (wave 5), `decodifica-parlanti` (wave 5), `confronto-impronte` (wave 4), `abbonato-revisione-parlanti` (wave 5), `abbonato-riallineamento-impronte` (wave 5), `riallinea-impronte` (wave 4)
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
    - `ImpronteRiallineate`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by riallinea-impronte after the commit of >= 1 refreshed print row (ADR 0012 Amendment (b)); consumers: proposta (cache invalidation), avvio-parlanti (AggiornamentiVista); NOT Documento (prints do not change it)
    - `TipoParlanteVista`: enum RICORRENTE | OCCASIONALE (parlanti:applicazione)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceRef`: composite (registrazioneId, voceId), typed kernel VO because >=2 contexts use it — correlation key of Attribuzione, ImprontaVocale and the Documento name map; stable as its parts
    - `ParlanteId`: minted by conferma-attribuzione (new Nome) and salta-voce via GeneratoreId (UUID v4) — stable across rinomina, promozione and eliminazione (tombstone keeps it); disappears only via INV-25 (occasionale left without Attribuzioni)
    - `ProgettoId`: minted by crea-progetto via kernel GeneratoreId (UUID v4 string) — immutable; stored in progetto.db so it survives moving/copying the project folder
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard
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

Sources: ADRs 0002, 0003, 0004, 0009, 0010, 0012, 0017 (.mismagent/decisions/); architecture.md (:avvio), ADR 0009/0012 Amendment (b), release pivot 2026-09-23 (R2 Parlanti).
