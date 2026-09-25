---
id: "avvio-parlanti"
type: "adapter"
context: "piattaforma"
side: "app"
wave: 14
release: "R2"
module: ":avvio"
consumes:
  - "kernel-pl"
  - "eventi-revisione"
  - "eventi-parlanti"
  - "tec-shell-ui"
  - "eventi-elaborazione"
  - "piano-per-somiglianza"
  - "ui-azioni-somiglianza"
  - "eventi-progetto"
  - "tec-pulizia-derivati"
  - "tec-sonda-archivio"
  - "tec-lettore-audio"
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
  - "sostituzione-trascritto-policy"
  - "piano-riassegnazione"
  - "classificatore-somiglianza"
  - "riassegna-segmenti"
  - "estrattore-impronta-sherpa"
  - "elimina-registrazione"
  - "completa-eliminazioni"
  - "eliminazione-registrazione-policy"
  - "abbonato-eliminazione-trascrizione"
  - "rigenerazione-documento"
  - "abbonato-documento"
  - "schermata-registrazioni"
  - "repository-sql-progetto"
  - "repository-sql-trascrizione"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0009"
  - "0010"
  - "0012"
  - "0014"
  - "0017"
  - "0018"
  - "0019"
  - "0020"
model_hint: "deep"
gated_by:
  - "ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)"
---
# avvio-parlanti — Composizione R2 (Parlanti): repository, abbonati, riallineamento impronte, S4, badge S2, pannello S3, eliminazione di una Registrazione

## What to do
R2 composition (release Parlanti, PAUSED): EXTENDS avvio-composizione's graph with the Parlanti SQL repositories and adapters, the revisione-policy sync subscriber, abbonato-revisione-parlanti and abbonato-riallineamento-impronte after commit, RiallineaTutteLeImpronte in background at project open (after RecuperaElaborazioniInterrotte, exceptions caught and logged, cancelled on close), lettore-nomi-da-parlanti replacing the empty LettoreNomi, the Parlanti shell section (S4), the S2 identification badge, the S3 Voci panel + Revisione UI (Parlanti sources and commands supplied to the S3 presenter), the native-Mutex wait of print extraction (AC-236), and the --smoke extension for S4.

REWORK 2026-09-24 (ADR 0018): TrascrittoSostituito after commit → invalidate every Proposta + Cambiamento(null) (AC-456); supply 'Ritrascrivi' to S2 and register the synchronous purge subscriber before the first command (AC-457); e2e AC-458, AC-459, AC-479.

REWORK 2026-09-24 (ADR 0019 + Amendment (b)): implement AzioniSomiglianza in the per-project scope — calcola → Anteprima (PianoRiassegnazioneQuery on a background dispatcher, nothing written), applica → RiassegnaSegmenti with the HELD plan (no recomputation, no extraction), annulla; the plan is dropped on Applica, Annulla, project close and Ritrascrivi — and ComandiVoce.nominaFrase (the ADR 0019 §5 steps). REALI wiring: EstrattoreImprontaSherpa(EmbeddingSherpa on embedding-nemo-titanet-small) + DecodificatoreAudioFfmpeg with proposte = true (provisional Fasce, user 2026-09-24), ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO)), the extractor's chiudi at project close, SegmentoConfermato → Cambiamento; the Assente stand-ins removed. Tests AC-537..AC-541, AC-549.

REWORK 2026-09-25 (ADR 0020): register AbbonatoEliminazioneRegistrazione + AbbonatoRevisioneParlanti before the first command and supply eliminaRegistrazione to S2 (AC-630); AggiornamentiVistaParlanti on RegistrazioneEliminata (AC-631); after-commit PuliziaRegistrazioneEliminata — pause, ArchivioAudio.scarta, cache/audio WAV, NavigazioneProgetto.dimentica (AC-632); implement PuliziaDerivatiRegistrazione and run CompletaEliminazioniRegistrazioni at open after RigeneraTuttiIDocumenti is queued (AC-633); e2e, refusals and race (AC-634..636).

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): R2 (Parlanti — PAUSED) half of the former monolithic composition root; EXTENDS avvio-composizione's graph. ADR 0012 Amendment (b): wire abbonato-riallineamento-impronte (after commit) and run RiallineaTutteLeImpronte at project open, in background, after RecuperaElaborazioniInterrotte; pass the same UnitaDiLavoro to the Parlanti commands and to RiallineaImpronte. Carry-over owned here: catch/log RiallineaTutteLeImpronte exceptions, never crash the background scope, cancel on project close (riallinea-impronte code-review F2, AC-358). The real EstrattoreImpronta (estrattore-impronta-sherpa) wires itself here. AMENDED 2026-09-24 (delta 2026-09-24-packaging, user decisions 2026-09-24): takes the attesa-mutex-estrazione gate moved from R1 (AC-236 moved here from avvio-coda-elaborazioni); supplies the Parlanti sources and the Revisione/identification commands to the S3 presenter by wiring schermata-registrazione-identificazione (the S3 identification panel + Revisione UI, cut from R1). AMENDED 2026-09-24 (ADR 0017, manifest delta 2026-09-24-mutex): gate attesa-mutex-estrazione SATISFIED; AC-236 rewritten with the chosen behaviour; AC-418..AC-421 (per-project command scope + per-VoceRef pending state and annulla for schermata-registrazione-identificazione AC-415, cancellation writes nothing, close joins pending work before the DB closes, one screen-scoped Proposta job). AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): AggiornamentiVistaParlanti handles TrascrittoSostituito after commit (invalidate every cached Proposta, Cambiamento(null)); supplies 'Ritrascrivi' to S2 (R2 only) and keeps AnnullaElaborazione supplied; the sync subscriber with the sostituzione policy is registered before the first command, so 'Ritrascrivi' is offered only by a composition that purges. AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): implements the UI port of ui-azioni-somiglianza (AzioniSomiglianza: calcola → preview, applica with the HELD plan, annulla; ComandiVoce.nominaFrase running the ADR 0019 §5 steps) — the cross-context glue of architecture.md, no domain rule; REALI wiring of the real extractor (TitaNet-small) and of the classifier (AC-541). Wave 11 → 14: it now depends_on estrattore-impronta-sherpa (wave 13); the extractor no longer wires itself (edge reversed; no state folder moved). AMENDED 2026-09-25 (ADR 0020, manifest delta 2026-09-25-elimina-registrazione, user decision 2026-09-25, defaults accepted): registers the two SYNCHRONOUS subscribers of RegistrazioneEliminata (AbbonatoEliminazioneRegistrazione + AbbonatoRevisioneParlanti) before the first command and supplies eliminaRegistrazione to S2 — so 'Elimina…' is offered only by a composition that purges both contexts; AggiornamentiVistaParlanti handles RegistrazioneEliminata; the after-commit PuliziaRegistrazioneEliminata (player pause, ArchivioAudio.scarta, cache/audio WAV, NavigazioneProgetto.dimentica — NavigazioneProgetto lives in avvio/r1, avvio-composizione's file, edited here) and the PuliziaDerivatiRegistrazione implementation (tec-pulizia-derivati) used by CompletaEliminazioniRegistrazioni at project open. Wave unchanged (14): every new dependency is at wave <= 8. model_hint deep: composition root folding >= 2 boundaries plus the race / crash-recovery e2e. RESOLVED 2026-09-25 (user checkpoint 2026-09-25): Q-1 — AC-634 counts one checkpoint per removal (3), not exactly one.

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
- AC-456 TrascrittoSostituito after commit → ProposteSerializzate.invalidaTutte() and ONE Cambiamento(null); never delivered on rollback
- AC-457 The R2 composition supplies 'Ritrascrivi' to the S2 presenter and registers AbbonatoRevisioneParlanti (with the sostituzione policy) BEFORE the first command — test: the subscriber is registered before CodaElaborazioni starts
- AC-458 E2E on databaseInMemoria with fake ML ports. Setup: Registrazione completata; Voce 1 → ricorrente 'Mario' with a print, Mario also attributed in another Registrazione; Voce 2 → occasionale 'Ospite del 12/09/2026' with a print, nowhere else. Action: Ritrascrivi with 2 persons, the fake pipeline completes with 3 Voci. Expected: zero attribuzione and zero impronta_vocale rows for that Registrazione; Mario exists with his other Attribuzione and print; the Ospite no longer exists; trascritto-view shows the 3 new Voci; the Documento file is rewritten with only 'Voce 1..3' labels and the new texts; the S2 row is 'Completata' with the badge '3 voci · 3 da identificare'
- AC-459 E2E failure, same setup: the fake pipeline fails in diarization; the attribuzione and impronta_vocale rows, the Trascritto, the Documento file bytes and the Galleria are unchanged; S2 shows 'Ritrascrizione non riuscita: errore nella separazione delle voci'
- AC-479 E2E, AC-458's setup: 'Ritrascrivi', then cancel while it is queued → the attribuzione/impronta_vocale rows, the Trascritto, the Documento file bytes and the Galleria are unchanged; no TrascrittoSostituito is published; S2 shows 'Completata' + 'Ritrascrivi'; S3 is editable again (AC-461)
- AC-537 (REWORDED 2026-09-24, Amendment (b).2) AzioniSomiglianza implemented in the per-project scope: calcola runs PianoRiassegnazioneQuery.calcola on a background dispatcher via runInterruptible, publishing InCorso progress, and ends in Anteprima(gruppi ordered by (a, da), incerte) — NOTHING written (row counts unchanged); applica runs RiassegnaSegmenti with the HELD plan (AC-549), publishing Applicazione then Esito(spostamenti.size, incerte) or Errore; at most one computation, preview or application per Registrazione (a second calcola meanwhile is ignored); annulla interrupts the computation or discards the preview, nothing written; the final transaction is not interrupted once started; closing the project cancels and joins, bounded like CollaboratoriR1.ferma, with no DB access after chiudi (AC-420 rule); AvviaElaborazione for the same Registrazione (Ritrascrivi queued) cancels the computation or discards the preview
- AC-538 (REWORDED, Amendment (b).1/(b).2) E2E on databaseInMemoria with fake ML ports (extractor vector per interval from a table). Setup: Voce 1 → Anna with one confirmed 2 s Segmento; Voce 2 → Marco with one confirmed Segmento; Voce 3 unattributed with 4 Segmenti (2 Anna-like, 1 Marco-like, 1 ambiguous); Voce 4 → Luca with NO confirmed Segmento and 3 Segmenti >= 1 s (2 Luca-like, 1 Anna-like: 'intera Voce'). calcola → Anteprima groups {3→1: 2, 3→2: 1, 4→1: 1}, incerte 1, attribuzione / segmento rows unchanged; applica → Anna-like Segmenti on Voce 1, Marco-like on Voce 2, the ambiguous one still on Voce 3, Voce 4 keeps its 2 Luca-like Segmenti and its Attribuzione, no confirmed Segmento moved; Esito(4, 1); exactly one Documento regeneration and one RiallineaImpronte of that Registrazione after commit. Confirm one Luca Segmento, calcola again → Anteprima with N = 0 and 'Chiudi' sends no command
- AC-539 (REWORDED) E2E stale plan: while the preview is shown a manual RiassegnaSegmento (called on the service) commits a planned Segmento elsewhere; applica → TrascrittoCambiato: nothing from the batch is written, the held plan is discarded, the state is Errore(TrascrittoCambiato), and a new calcola computes a fresh plan from the new state
- AC-540 E2E 'Dai un nome a una frase' case (d): nominaFrase(NuovaVoce(nuovo 'Dario')) on a Segmento of a 3-Segmento unattributed Voce → a new Voce holds that Segmento, confermato, attributed to the new ricorrente 'Dario' with one print row; the Documento shows 'Dario' on that line; with 'Dario' already used by an attivo the new Voce exists unnamed, the error is NomeGiaInUso and nothing else is written
- AC-541 REALI wiring: EstrattoreImprontaSherpa(EmbeddingSherpa on embedding-nemo-titanet-small) + DecodificatoreAudioFfmpeg, proposte = true (provisional Fasce, user 2026-09-24); ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO)); EstrattoreImprontaAssente / DecodificatoreAudioAssente removed; the extractor's chiudi called at project close; SegmentoConfermato → one Cambiamento(registrazioneId) after commit; existing 'nessun-estrattore' print rows re-derived by RiallineaTutteLeImpronte at the next open (AC-316 test with such a row)
- AC-549 (NEW, Amendment (b).2) The held plan: applica sends EXACTLY the spostamenti of the last calcola (1:1 into SpostamentoSegmento) with 0 calls to EstrattoreImpronta / DecodificatoreAudio during applica (counting fakes); applica with no preview or with N = 0 → no command; the plan is dropped after applica (whatever the outcome), on annulla, on project close and on AvviaElaborazione of the same Registrazione, after which applica sends nothing; the plan holds no Impronta (code review, ADR 0009)
- AC-630 The R2 composition registers AbbonatoEliminazioneRegistrazione (Trascrizione) and AbbonatoRevisioneParlanti BEFORE the first command, and supplies eliminaRegistrazione to the S2 presenter. Test: both synchronous subscribers are registered before CodaElaborazioni starts. The R1 composition (avvio-composizione) supplies no eliminaRegistrazione (AC-625, last clause)
- AC-631 AggiornamentiVistaParlanti: RegistrazioneEliminata after commit → ProposteSerializzate.invalidaTutte() + ONE Cambiamento(null); never delivered on rollback
- AC-632 PuliziaRegistrazioneEliminata (after commit), in this order: (1) if the LettoreAudio holds that registrazioneId it pauses it; (2) ArchivioAudio.scarta(riferimentoAudio); (3) deletes cache/audio/<id>.wav (idempotent); (4) NavigazioneProgetto.dimentica(id): a current place Registrazione(id), or a primaDiModelli of Registrazione(id), becomes Registrazioni, other places untouched. An I/O failure is logged and does not throw; the pending row stays for AC-633
- AC-633 Startup sequence: after RigeneraTuttiIDocumenti is queued, CompletaEliminazioniRegistrazioni.esegui() runs with PuliziaDerivatiRegistrazione implemented here (delete cache/audio/<id>.wav, then RigenerazioneDocumentoPolitica.perRegistrazioneEliminata(id, data, titolo, ∅)). Test on a project folder where a pending row exists and audio/<id>.m4a, cache/audio/<id>.wav and documenti/<nomeFile> are all still present (simulated crash after commit): after opening the three files are gone and eliminazione_in_sospeso is empty; an unrelated user file documenti/appunti.md is untouched
- AC-634 (INV-28) E2E on a real SQLite file with the fake ML ports. Setup: Registrazione R completata with Voce 1 → ricorrente 'Mario' (print), also attributed with a print in Registrazione Q; Voce 2 → occasionale 'Ospite del 12/09/2026' (print) seen only in R; Voce 3 → an eliminato tombstone's Attribuzione; a Documento file for R, and the audio/ and cache/audio/ files for R. Action: EliminaRegistrazione(R), then wait for the after-commit work. Expected: zero rows keyed by R in registrazione, elaborazione, trascritto, voce, segmento, attribuzione and impronta_vocale; Mario exists with his Q Attribuzione and print (numImpronte 1, numRegistrazioni 1 in parlanti-del-progetto); the Ospite no longer exists; the tombstone still exists (still eliminato, same Nome); audio/R.*, cache/audio/R.wav and R's .md are gone while Q's files are untouched; S2 lists only Q; one wal_checkpoint(TRUNCATE) per removal ran, all after the commit and none before it: exactly 3 here — Mario's salva (one print removed), the Ospite's salva (one print removed) and the Ospite's rimuovi (INV-25); the tombstone's salva removes no print and adds none (AMENDED 2026-09-25, user Q-1 'once per removal', AC-622). The eliminazione_in_sospeso row for R still exists until the next open, and after reopening it is empty
- AC-635 (INV-28) E2E refusals, same setup plus a second Registrazione S: S in_corso (held pipeline) → EliminaRegistrazione(S) returns ElaborazioneGiaAperta; S re-queued in_attesa → the same. In both cases every row and file of S is unchanged, no eliminazione_in_sospeso row exists, and no after-commit event is delivered. After AnnullaElaborazione of the queued run, EliminaRegistrazione(S) → Ok
- AC-636 Race on a real SQLite FILE database with two UnitaDiLavoroSql threads started on a barrier, repeated 100 times: EliminaRegistrazione(R) against AvviaElaborazione(R). Every run ends in exactly one of: R deleted and the start got RegistrazioneNonTrovata with no elaborazione row; or R intact with one in_attesa and the deletion got ElaborazioneGiaAperta. Never both, never a thrown exception or an FK failure

## Dependencies
- **GATED — not ready until:** ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)
- Blocks built first: `avvio-composizione` (wave 10), `schermata-parlanti` (wave 8), `schermata-registrazioni-identificazione` (wave 9), `schermata-registrazione-identificazione` (wave 9), `repository-sql-parlanti` (wave 4), `registrazione-da-progetto-pa` (wave 5), `lettore-voci-da-trascrizione` (wave 5), `lettore-nomi-da-parlanti` (wave 5), `decodifica-parlanti` (wave 5), `confronto-impronte` (wave 4), `abbonato-revisione-parlanti` (wave 5), `abbonato-riallineamento-impronte` (wave 5), `riallinea-impronte` (wave 4), `sostituzione-trascritto-policy` (wave 4), `piano-riassegnazione` (wave 5), `classificatore-somiglianza` (wave 4), `riassegna-segmenti` (wave 4), `estrattore-impronta-sherpa` (wave 13), `elimina-registrazione` (wave 4), `completa-eliminazioni` (wave 4), `eliminazione-registrazione-policy` (wave 4), `abbonato-eliminazione-trascrizione` (wave 5), `rigenerazione-documento` (wave 5), `abbonato-documento` (wave 6), `schermata-registrazioni` (wave 8), `repository-sql-progetto` (wave 6), `repository-sql-trascrizione` (wave 6)
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
- **eventi-revisione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `revisione (VociUnite, VoceDivisa, SegmentoRiassegnato, SegmentoConfermato), riassegna-segmenti (SegmentoRiassegnato, N per batch)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `VociUnite`: data class(registrazioneId: RegistrazioneId, sopravvissuta: VoceId, rimossa: VoceId) : EventoPubblicato
    - `VoceDivisa`: data class(registrazioneId: RegistrazioneId, origine: VoceId, nuova: VoceId, segmentiSpostati: List<SegmentoId>) : EventoPubblicato
    - `SegmentoRiassegnato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, da: VoceId, a: VoceId, daRimossa: Boolean, aNuova: Boolean) : EventoPubblicato
    - `SegmentoConfermato`: data class(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, confermato: Boolean) : EventoPubblicato — after commit only (view refresh); no synchronous subscriber (ADR 0019 §3)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
  - delivery: Parlanti revisione-policy → in-process, SYNCHRONOUS inside the publishing command's UnitaDiLavoro transaction, in emission order, exactly once per commit attempt; an Esito.Errore or exception from a sync subscriber rolls the whole command back (ADR 0012). Documento / UI refresh / Parlanti RiallineaImpronte (abbonato-riallineamento-impronte) → in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. ADR 0019: a RiassegnaSegmenti commit publishes N SegmentoRiassegnato in list order — the synchronous revisione-policy runs once per event inside the one transaction (an Errore rolls the whole batch back); after-commit subscribers are coalesced per registrazioneId as today (one Rigenerazione, one RiallineaImpronte). SegmentoConfermato → after commit only
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
- **eventi-elaborazione** (consumed/implemented) — owner `eventi-pubblicati`, supplier `esegui-elaborazione (Avviata/Completata/Fallita/TrascrittoSostituito), annulla-elaborazione (ElaborazioneAnnullata)`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `ElaborazioneAvviata`: data class(registrazioneId: RegistrazioneId, avviataAlle: Instant) : EventoPubblicato
    - `ElaborazioneCompletata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato
    - `ElaborazioneFallita`: data class(registrazioneId: RegistrazioneId, motivo: String) : EventoPubblicato — motivo in plain Italian
    - `TrascrittoSostituito`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata (ADR 0018)
    - `ElaborazioneAnnullata`: data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published by AnnullaElaborazione in the cancelling transaction (a never-started in_attesa row deleted), AFTER COMMIT only; no synchronous subscriber (ADR 0018 Amendment (b))
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
  - delivery: in-process, AFTER COMMIT only (never on rollback), at-least-once, on a background coroutine, coalesced per registrazioneId; subscribers must be idempotent (INV-23); single writer per key (one process, one DB) so no cross-stream reordering hazard. EXCEPTION (ADR 0018/0012): TrascrittoSostituito has one SYNCHRONOUS subscriber (Parlanti purge, abbonato-revisione-parlanti → sostituzione-trascritto-policy) inside the publishing transaction; its other subscribers are after commit
- **piano-per-somiglianza** (consumed/implemented) — owner `piano-riassegnazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `PianoRiassegnazioneQuery`: fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione> — errors TrascrittoNonTrovato, ErroreParlanti.RiferimentiInsufficienti(registrazioneId); throws InterruptedException on cancellation; never opens a transaction; writes nothing
    - `PianoRiassegnazione`: data class(registrazioneId: RegistrazioneId, spostamenti: List<SpostamentoProposto>, incerte: Int) — no similarity number, no Impronta: the glue may HOLD it until Applica (ADR 0019 Amendment (b).2)
    - `SpostamentoProposto`: data class(segmentoId: SegmentoId, da: VoceId, a: VoceId, intervallo: IntervalloMs) — primitives + kernel VOs only; the glue maps it 1:1 to SpostamentoSegmento; ordered by (intervallo.inizioMs, segmentoId)
    - `ErroreParlanti.RiferimentiInsufficienti`: data class(registrazioneId: RegistrazioneId) : ErroreParlanti (ErroriParlanti.kt) — fewer than 2 reference Parlanti (INV-27)
  - keys (minting rules):
    - `VoceId`: minted by the trascritto aggregate from its persisted counter prossimaVoce — at creation 1..n in order of FIRST APPEARANCE (smallest turn inizioMs, tie: diarizer voceIndice); DividiVoce / riassegna-to-new take prossimaVoce++; never reused, never renumbered, == the n of the label 'Voce n'; stable for the life of one Trascritto GENERATION: a Ritrascrivi replacement (ADR 0018) is a fresh Trascritto.crea numbered from 1 again, and every VoceRef-keyed Parlanti row of the old generation is purged in the same transaction (TrascrittoSostituito)
    - `SegmentoId`: minted by the trascritto aggregate at creation only, 1..m in order (inizioMs, then voceId); no Segmento is ever created afterwards (INV-8) — stable for the Trascritto generation's life (ADR 0018: a replacement renumbers from 1)
- **ui-azioni-somiglianza** (consumed/implemented) — owner `schermata-registrazione-identificazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `AzioniSomiglianza`: interface (snastro.ui.registrazione) { fun calcola(id: RegistrazioneId); fun applica(id: RegistrazioneId); fun annulla(id: RegistrazioneId); val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>> } — calcola ends in Anteprima and writes nothing; applica sends the HELD plan (ignored unless Anteprima with N > 0); annulla interrupts a computation or discards a preview; no entry = idle (ADR 0019 Amendment (b).2)
    - `StatoSomiglianza`: sealed interface { InCorso(fatti: Int, totale: Int, ultimoAvanzamentoMs: Long); Anteprima(gruppi: List<GruppoSpostamenti>, incerte: Int); Applicazione; Esito(spostate: Int, incerte: Int); Errore(errore: ErroreSomiglianzaUi) }
    - `GruppoSpostamenti`: data class(da: VoceId, a: VoceId, frasi: Int) — ordered by (a, da); N = sum of frasi
    - `ErroreSomiglianzaUi`: sealed interface { data object TrascrittoCambiato; data object RiferimentiInsufficienti; data class Altro(testo: String) }
    - `ComandiVoce.nominaFrase`: (registrazioneId: RegistrazioneId, segmentoId: SegmentoId, passi: PassiNominaFrase) — same per-project scope and pending state as ADR 0017 §3, keyed by the Segmento; the steps are separate commands, not one transaction (ADR 0019 §5)
    - `PassiNominaFrase`: sealed interface (UI side, computed by the presenter) { SoloConferma; AttribuisciVoce(voceId: VoceId, obiettivo: ObiettivoNome); Sposta(voceId: VoceId); NuovaVoce(obiettivo: ObiettivoNome) }
    - `ObiettivoNome`: sealed interface { Esistente(parlanteId: ParlanteId); Nuovo(nome: String, ricorrente: Boolean) } — 'nuovo…' of Q-7
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
- **tec-pulizia-derivati** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `PuliziaDerivatiRegistrazione`: interface { fun pulisci(e: EliminazioneInSospeso): Esito<Unit> } — removes every DERIVED file of a deleted Registrazione (cache/audio/<id>.wav, the Documento .md via RigenerazioneDocumentoPolitica.perRegistrazioneEliminata); idempotent (absent files = Ok); implemented in :avvio (avvio-parlanti); an Errore leaves the pending row for the next project open
    - `EliminazioneInSospeso`: see repo-progetto — data class(registrazioneId: RegistrazioneId, titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio)
  - keys (minting rules):
    - `RegistrazioneId`: minted by servizi-registrazione (AggiungiRegistrazione) via GeneratoreId (UUID v4) — immutable; also names audio/<id>.<ext>, cache/audio/<id>.wav and every EstrattoRef
    - `nomeFile`: minted by documento.nomeFile(dataRegistrazione, titolo) (see tec-scrittore-documento) from the EliminazioneInSospeso values — the name at deletion
- **tec-sonda-archivio** (consumed/implemented) — owner `porte-progetto`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `SondaAudio`: interface { fun sonda(percorsoSorgente: String): Esito<InfoAudio> } — Errore(AudioNonLeggibile | FormatoNonSupportato)
    - `InfoAudio`: data class(durataMs: Long, dataFile: LocalDate)
    - `ArchivioAudio`: interface { fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>; fun scarta(r: RiferimentoAudio) } — Errore(CopiaFallita) leaves no partial file
  - keys (minting rules):
    - `RiferimentoAudio`: minted by audio-progetto (ArchivioAudio.copia): 'audio/<registrazioneId>.<source extension lowercased>', relative to the project folder — immutable
- **tec-lettore-audio** (consumed/implemented) — owner `lettore-audio`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `LettoreAudio`: interface { fun disponibile(id: RegistrazioneId): Boolean; fun riproduciDa(id: RegistrazioneId, daMs: Long); fun riproduciEstratto(e: EstrattoRef); fun pausa(); val stato: StateFlow<StatoLettore> }
    - `StatoLettore`: data class(registrazioneId: RegistrazioneId?, posizioneMs: Long, inRiproduzione: Boolean)

Sources: ADRs 0002, 0003, 0004, 0005, 0009, 0010, 0012, 0014, 0017, 0018, 0019, 0020 (.mismagent/decisions/); architecture.md (:avvio), ADR 0009/0012 Amendment (b), release pivot 2026-09-23 (R2 Parlanti).
