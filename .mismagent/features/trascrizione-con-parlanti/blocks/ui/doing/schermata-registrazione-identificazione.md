---
id: "schermata-registrazione-identificazione"
type: "ui"
context: "ui"
side: "app"
wave: 9
release: "R2"
module: ":ui (snastro.ui.registrazione)"
consumes:
  - "kernel-pl"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "schermata-registrazione"
  - "identificazione-voci"
  - "proposta"
  - "proposta-unione"
  - "parlanti-attivi"
  - "estratto-audio"
  - "conferma-attribuzione"
  - "salta-voce"
  - "revisione"
  - "ui-fondamenta"
  - "trascritto-view"
related_adrs:
  - "0002"
  - "0003"
  - "0005"
  - "0010"
  - "0012"
  - "0017"
  - "0018"
  - "0019"
consumes_rm:
  - "identificazione-voci"
  - "proposta"
  - "proposta-unione"
  - "parlanti-attivi"
  - "estratto-audio"
  - "trascritto-view"
triggers:
  - "ConfermaAttribuzione"
  - "SaltaVoce"
  - "UnisciVoci"
  - "DividiVoce"
  - "RiassegnaSegmento"
  - "ConfermaSegmento"
  - "RiassegnaSegmenti"
gated_by:
  - "ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)"
owns_boundaries:
  ui-azioni-somiglianza:
    projection: "in-process"
    contract_test: "consumer-driven"
    pinned_types:
      AzioniSomiglianza: "interface (snastro.ui.registrazione) { fun calcola(id: RegistrazioneId); fun applica(id: RegistrazioneId); fun annulla(id: RegistrazioneId); val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>> } — calcola ends in Anteprima and writes nothing; applica sends the HELD plan (ignored unless Anteprima with N > 0); annulla interrupts a computation or discards a preview; no entry = idle (ADR 0019 Amendment (b).2)"
      StatoSomiglianza: "sealed interface { InCorso(fatti: Int, totale: Int, ultimoAvanzamentoMs: Long); Anteprima(gruppi: List<GruppoSpostamenti>, incerte: Int); Applicazione; Esito(spostate: Int, incerte: Int); Errore(errore: ErroreSomiglianzaUi) }"
      GruppoSpostamenti: "data class(da: VoceId, a: VoceId, frasi: Int) — ordered by (a, da); N = sum of frasi"
      ErroreSomiglianzaUi: "sealed interface { data object TrascrittoCambiato; data object RiferimentiInsufficienti; data class Altro(testo: String) }"
      "ComandiVoce.nominaFrase": "(registrazioneId: RegistrazioneId, segmentoId: SegmentoId, passi: PassiNominaFrase) — same per-project scope and pending state as ADR 0017 §3, keyed by the Segmento; the steps are separate commands, not one transaction (ADR 0019 §5)"
      PassiNominaFrase: "sealed interface (UI side, computed by the presenter) { SoloConferma; AttribuisciVoce(voceId: VoceId, obiettivo: ObiettivoNome); Sposta(voceId: VoceId); NuovaVoce(obiettivo: ObiettivoNome) }"
      ObiettivoNome: "sealed interface { Esistente(parlanteId: ParlanteId); Nuovo(nome: String, ricorrente: Boolean) } — 'nuovo…' of Q-7"
---
# schermata-registrazione-identificazione — S3 · pannello Voci e Revisione (fetta Parlanti di S3)

## What to do
R2 slice of S3 (the S3 PANEL, not the S2 badge block schermata-registrazioni-identificazione): supplies the S3 presenter's optional Parlanti sources and commands — the Voci panel on the right (one card per Voce: '▶ estratto', Proposta candidates with Fascia bar, 'Conferma', 'altri ▾', 'nuovo…', 'salta' / 'cambia', 'Unisci con ▾', merge banner) and the transcript selection toolbar ('Riassegna a ▾', 'Dividi voce'); labels show the attributed Nome from identificazione-voci. Gated on the attesa-mutex-estrazione ADR, which decides the user-facing wait on the native Mutex.

REWORK 2026-09-24 (ADR 0018): while S3 is read-only every editing action of the panel is disabled and no Proposta job runs (AC-454); everything is enabled again when the re-run fails or is cancelled (AC-461); pending Conferma/Salta from before the queueing resolve as VoceCambiata (AC-455).

REWORK 2026-09-24 (ADR 0019 + Amendment (b)): 'Dai un nome a questa frase ▾' on a single selected Segmento (the presenter's case decision (a)-(d), always through ComandiVoce.nominaFrase), the pin marker + 'Togli conferma' (ConfermaSegmento(false)), and the 'Riassegna per somiglianza' button in the Voci panel header: enabling rule and reference-mode lines, computing state ('Confronto le frasi… n di N', 'In attesa dell'elaborazione…', 'Annulla'), PREVIEW ('Sposterò N frasi, M incerte restano dove sono' + one line per da → a pair; Applica / Annulla, or Chiudi when N = 0), result texts, and 'Ricalcola' on TrascrittoCambiato; editing disabled while computing, previewing or applying. Declares the UI port AzioniSomiglianza and ComandiVoce.nominaFrase (boundary ui-azioni-somiglianza, owned here). Tests AC-526..AC-536, AC-545..AC-548.

Note: NEW 2026-09-24 (manifest delta 2026-09-24-packaging, user decisions 2026-09-24 — variant A): the Parlanti + Revisione slice of S3 split out of schermata-registrazione so R1 ships S3 read-only (AC-402), following the S2 precedent (schermata-registrazioni / schermata-registrazioni-identificazione). NAMING: this is the S3 PANEL block (singular 'registrazione'); the S2 BADGE block is schermata-registrazioni-identificazione (plural) — both R2, both wired by avvio-parlanti. It extends the S3 presenter (snastro.ui.registrazione) by supplying the optional sources/commands of AC-402: Voci panel (cards, Proposta, 'Conferma', 'altri ▾', 'nuovo…', 'salta', 'cambia', 'Unisci con ▾', merge banner, '▶ estratto') and the transcript selection toolbar ('Riassegna a ▾', 'Dividi voce'). GATED on attesa-mutex-estrazione: this is where the user-facing wait on the native Mutex lives (ConfermaAttribuzione/SaltaVoce/Proposta extract prints); the ADR closing the spike folds its chosen behaviour into these tests_nl. 'salta' is not offered on an attributed Voce (use 'cambia', R24). AMENDED 2026-09-24 (ADR 0017, manifest delta 2026-09-24-mutex): gate attesa-mutex-estrazione SATISFIED; the chosen wait behaviour is AC-411..AC-417 (per-card pending state, 'In attesa dell'elaborazione…' + 'Annulla' after SOGLIA_ATTESA_VISIBILE_MS = 2000 ms, defined once in the S3 presenter). OWNER (rule 10): this block declares, in snastro.ui.registrazione, the per-VoceRef pending-state source with annulla(voceRef) that the presenter reads; avvio-parlanti implements it in a per-project scope (AC-418). 'Annulla' cancels a pending command, it is not a domain command (no new triggers entry). AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): S3 is READ-ONLY while a re-run is queued or running (user 2026-09-24, replaces ADR 0018 §4 'fully usable'): the panel reads the read-only flag of the S3 presenter (schermata-registrazione, AC-452) and disables every command it triggers; this closes ADR 0018's accepted residual race for S3. No Proposta job while read-only (it would only wait on the Mutex held by the pipeline, ADR 0017). AMENDED 2026-09-24 (ADR 0019 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-semi-automatica): 'Dai un nome a questa frase ▾', the pin marker + 'Togli conferma', and the 'Riassegna per somiglianza' button with compute → PREVIEW → Applica / Annulla (user 2026-09-24). OWNER (rule 10): this block declares in snastro.ui.registrazione the UI port AzioniSomiglianza and ComandiVoce.nominaFrase (boundary ui-azioni-somiglianza); avvio-parlanti implements them in the per-project scope. 'Annulla' / 'Chiudi' / 'Ricalcola' are not domain commands (no triggers entry).

### Consumes read-models: identificazione-voci, proposta, proposta-unione, parlanti-attivi, estratto-audio, trascritto-view
### Triggers: ConfermaAttribuzione, SaltaVoce, UnisciVoci, DividiVoce, RiassegnaSegmento, ConfermaSegmento, RiassegnaSegmenti

## Tasks
- AC-209 La selezione è limitata a una Voce
- AC-210 'Dividi voce' disabilitato con spiegazione se la selezione è l'intera Voce
- AC-211 Riassegna verso una Voce esistente o 'nuova voce'
- AC-212 Galleria vuota → solo 'nuovo…' / 'salta' con il suggerimento 'Prima registrazione: dai un nome alle voci'
- AC-213 Tutti i Candidati 'nessuna' → lista mostrata e 'nuovo…' evidenziato
- AC-214 Fascia come barra, mai un numero
- AC-215 Errore di comando (es. nome già usato) → messaggio inline sulla card e nulla cambia
- AC-216 Banner di proposta di unione: un click unisce e il banner scompare quando la condizione cade
- AC-219 Una Voce attribuita mostra 'cambia' e non 'salta'
- AC-318 Errore VoceCambiata su 'conferma' o 'salta' → messaggio in linguaggio semplice sulla card ('La voce è cambiata nel frattempo: riprova'), nulla cambia e il comando può essere ripetuto
- AC-319 Dopo ImpronteRiallineate della Registrazione aperta la Proposta visibile è ricaricata senza perdere la selezione
- AC-403 (ex metà estratti di AC-217) Audio sorgente mancante → i '▶ estratto' delle Voci e dei Candidati sono disabilitati con messaggio; il pannello resta utilizzabile per conferma/salta/nuovo
- AC-404 (ex metà Revisione di AC-215) Un errore di un comando di Revisione (UnisciVoci, DividiVoce, RiassegnaSegmento → Esito.Errore) è mostrato come messaggio in linguaggio semplice inline nel trascritto; il trascritto e la selezione restano invariati
- AC-405 (stati del pannello) Con le sorgenti Parlanti fornite (R2) ma non ancora caricate il pannello mostra un indicatore di caricamento per card, mai un conteggio provvisorio né una galleria vuota; un errore di lettura di una sorgente mostra un messaggio nella card interessata e lascia il trascritto (AC-402) utilizzabile; le etichette mostrano il Nome attribuito (identificazione-voci) al posto di 'Voce n'
- AC-411 (ADR 0017 §3) 'Conferma' / 'altri ▾ → Conferma' / 'nuovo…' / 'salta' / 'cambia' mettono subito la card nello stato in corso: i pulsanti d'azione della card sono disabilitati (nessun secondo comando dalla stessa card), è mostrato un indicatore di avanzamento, le altre card e il trascritto restano utilizzabili
- AC-412 (ADR 0017 §3) Un comando ancora in corso dopo SOGLIA_ATTESA_VISIBILE_MS (2000 ms, costante definita una sola volta nel presenter di S3) fa mostrare alla card 'In attesa dell'elaborazione…' con un pulsante 'Annulla'; nessuna percentuale, nessun conto alla rovescia — test con un comando finto trattenuto da un latch e tempo virtuale
- AC-413 'Annulla' annulla il comando in corso: la card torna allo stato precedente (stessa Proposta, pulsanti abilitati), nessun messaggio d'errore, e il comando finto vede l'annullamento (nulla è scritto dalla sua parte)
- AC-414 L'esito di un comando in corso è mostrato come quello di ogni comando: il Nome attribuito, l'errore inline di AC-215 o VoceCambiata di AC-318; la barra della Revisione non è disabilitata mentre un comando è in corso
- AC-415 Lo stato in corso viene dalla sorgente per progetto fornita da avvio-parlanti (per VoceRef): un presenter ricreato mentre un comando è ancora in corso (l'utente è uscito da S3 ed è tornato) mostra di nuovo quella card in corso, o in attesa oltre la soglia
- AC-416 Una Proposta non ancora arrivata dopo SOGLIA_ATTESA_VISIBILE_MS fa mostrare alla card 'Proposta in attesa dell'elaborazione…' al posto dei Candidati, senza 'Annulla'; 'altri ▾', 'nuovo…' e 'salta' restano abilitati durante l'attesa
- AC-417 Il presenter non esegue mai un comando né una Proposta sul thread UI: il comando finto registra il suo thread e verifica che non sia il dispatcher UI né quello main del test; è eseguito tramite il dispatcher di background iniettato
- AC-454 (Amendment 2026-09-24 (b): S3 read-only, user) While S3 is read-only (AC-452) the banner adds 'Le correzioni e i nomi assegnati andranno persi.' and every editing action of the panel is disabled: 'Conferma', 'altri ▾', 'nuovo…', 'cambia', 'salta', 'Unisci con ▾', the merge banner's action, the selection toolbar's 'Dividi voce' and 'Riassegna a ▾'; no command is invoked (the command fakes record zero calls) and no Proposta job is started; '▶ estratto' and Segmento playback still work — presenter test with the stati source at IN_ATTESA and at IN_CORSO
- AC-455 A Conferma or Salta already pending in the per-project scope (AC-418) from BEFORE the re-run was queued, resolving after the replacement, shows AC-318 VoceCambiata or the Voce-not-found message in plain words, never a crash; after the replacement the panel shows the new Voci, all 'da identificare', and is editable again
- AC-461 When the read-only state of AC-452 ends because the re-run FAILED or was CANCELLED (Cambiamento → latest FALLITA or COMPLETATA, Trascritto unchanged) the banner goes away and every action of AC-454 is enabled again, on the same Voci and the same attributed Nomi (nothing lost); the Proposta job starts again — presenter test: stati source IN_CORSO → FALLITA and IN_ATTESA → COMPLETATA
- AC-526 (ADR 0019 §6) With exactly ONE Segmento selected the toolbar shows 'Dai un nome a questa frase ▾': the menu lists the attivo Parlanti (parlanti-attivi), then 'nuovo…' (Nome field, ricorrente preselected, occasionale toggle, Q-7); the action is absent with 0 or >= 2 Segmenti selected and disabled while S3 is read-only (AC-454)
- AC-527 The presenter's case decision (pure; table test on fixture views) matches ADR 0019 §5: (a) the Segmento is on a Voce attributed to P → SoloConferma; (b) it is alone in its Voce → AttribuisciVoce(thatVoce, P); (c) P has Voci here → Sposta(lowest voceId of P); (d) otherwise → NuovaVoce(P | nuovo Nome); every case goes through ComandiVoce.nominaFrase, the presenter never calls a command directly
- AC-528 A confermato Segmento renders a pin marker with the tooltip 'Frase confermata: «Riassegna per somiglianza» non la sposta'; selected alone, the toolbar offers 'Togli conferma' → ConfermaSegmento(false)
- AC-529 While a nominaFrase is pending, that Segmento row shows the ADR 0017 pending state: progress at once; 'In attesa dell'elaborazione…' + 'Annulla' after SOGLIA_ATTESA_VISIBILE_MS; an error (e.g. NomeGiaInUso on step (d)) inline in plain words, the new Voce appearing unnamed
- AC-530 (REWORDED 2026-09-24, Amendment (b).1/(b).6) A 'Riassegna per somiglianza' button in the Voci panel header, enabled iff: >= 2 attivo Parlanti attributed in this Registrazione each have >= 1 Segmento >= 1 000 ms on their Voci (a reference in either mode; derived from trascritto-view + identificazione-voci); S3 is not read-only; no Parlanti command or nominaFrase is pending; no computation, preview or application is in progress. Disabled hint 'Dai un nome ad almeno due persone'. Under the button 'Riferimenti: <Nomi> (frasi confermate) · <Nomi> (tutta la voce)' (a person is 'frasi confermate' iff they have a confirmed Segmento >= 1 s); with >= 1 'tutta la voce' person the line 'Senza una frase confermata uso tutta la voce: il risultato può cambiare se ripeti. Conferma una frase per persona per renderlo stabile.'; attributed attivo Parlanti with no >= 1 s Segmento → 'Non toccate: <Nomi>' (table test on fixture views)
- AC-531 Computing (fake AzioniSomiglianza held by a latch, virtual time): the button area shows 'Confronto le frasi… n di N' with a determinate bar; every editing action of the panel and of the selection toolbar is disabled and no command is invoked (fakes record zero calls); playback and '▶ estratto' still work; with no progress tick for SOGLIA_ATTESA_VISIBILE_MS 'In attesa dell'elaborazione…' is added; 'Annulla' is visible for the whole computation
- AC-532 'Annulla' during the computation → AzioniSomiglianza.annulla(id); the panel returns to its previous state with no error message
- AC-533 (REWORDED, Amendment (b).2) Result texts after Applica: Esito(3, 2) → '3 frasi spostate, 2 incerte (rimaste dov'erano)'; Esito(1, 1) → '1 frase spostata, 1 incerta (rimasta dov'era)'; Errore(TrascrittoCambiato) → 'La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima' (AC-547); any other error → a plain message (AC-404 rule); dismissible, cleared by the next run or by leaving S3
- AC-534 The state (computation, preview, application) comes from the per-project AzioniSomiglianza.stato: a presenter recreated during a computation (the user left S3 and came back) shows it running again with its progress
- AC-535 When S3 turns read-only (AC-452) during a computation, the presenter calls annulla(id)
- AC-536 The presenter never runs calcola, applica or nominaFrase on the UI thread (AC-417 rule; the fakes record their thread)
- AC-545 (NEW, Amendment (b).2) Preview: Anteprima(gruppi, incerte) renders 'Sposterò N frasi, M incerte restano dove sono' (N = sum of frasi; singulars '1 frase', '1 incerta resta dove è'), one line per group '<da> → <a>: <frasi>' with each Voce's current name (identificazione-voci) or 'Voce n', grouped by destination and ordered by (a, da), and the buttons 'Applica' / 'Annulla'; with N = 0 'Nessuna frase da spostare (M incerte restano dove sono)' and only 'Chiudi'; while the preview is shown every editing action of the panel and toolbar is disabled (fakes record zero command calls) and playback / '▶ estratto' work
- AC-546 (NEW) 'Applica' → AzioniSomiglianza.applica(id) exactly once (a double click → one call); during Applicazione both buttons are disabled and no 'Annulla' is offered; 'Annulla' on the preview → annulla(id), the panel returns to its previous state with no message and zero commands; 'Chiudi' (N = 0) → annulla(id)
- AC-547 (NEW) Errore(TrascrittoCambiato) after Applica → the AC-533 text plus a 'Ricalcola' button → calcola(id); no other command is sent and the old preview is never shown again
- AC-548 (NEW) A presenter recreated while in Anteprima (the user left S3 and came back) shows the same preview; when S3 turns read-only (AC-452) during a computation OR a preview the presenter calls annulla(id)
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- **GATED — not ready until:** ADR closing spike attesa-mutex-estrazione — satisfied: ADR 0017 (accepted 2026-09-24)
- Blocks built first: `schermata-registrazione` (wave 8), `identificazione-voci` (wave 5), `proposta` (wave 5), `proposta-unione` (wave 5), `parlanti-attivi` (wave 5), `estratto-audio` (wave 4), `conferma-attribuzione` (wave 4), `salta-voce` (wave 4), `revisione` (wave 4), `ui-fondamenta` (wave 6), `trascritto-view` (wave 5)
- **ui-azioni-somiglianza** (OWNED here — built before its consumers) — owner `schermata-registrazione-identificazione`, projection in-process, contract_test **consumer-driven**
  - pinned types:
    - `AzioniSomiglianza`: interface (snastro.ui.registrazione) { fun calcola(id: RegistrazioneId); fun applica(id: RegistrazioneId); fun annulla(id: RegistrazioneId); val stato: StateFlow<Map<RegistrazioneId, StatoSomiglianza>> } — calcola ends in Anteprima and writes nothing; applica sends the HELD plan (ignored unless Anteprima with N > 0); annulla interrupts a computation or discards a preview; no entry = idle (ADR 0019 Amendment (b).2)
    - `StatoSomiglianza`: sealed interface { InCorso(fatti: Int, totale: Int, ultimoAvanzamentoMs: Long); Anteprima(gruppi: List<GruppoSpostamenti>, incerte: Int); Applicazione; Esito(spostate: Int, incerte: Int); Errore(errore: ErroreSomiglianzaUi) }
    - `GruppoSpostamenti`: data class(da: VoceId, a: VoceId, frasi: Int) — ordered by (a, da); N = sum of frasi
    - `ErroreSomiglianzaUi`: sealed interface { data object TrascrittoCambiato; data object RiferimentiInsufficienti; data class Altro(testo: String) }
    - `ComandiVoce.nominaFrase`: (registrazioneId: RegistrazioneId, segmentoId: SegmentoId, passi: PassiNominaFrase) — same per-project scope and pending state as ADR 0017 §3, keyed by the Segmento; the steps are separate commands, not one transaction (ADR 0019 §5)
    - `PassiNominaFrase`: sealed interface (UI side, computed by the presenter) { SoloConferma; AttribuisciVoce(voceId: VoceId, obiettivo: ObiettivoNome); Sposta(voceId: VoceId); NuovaVoce(obiettivo: ObiettivoNome) }
    - `ObiettivoNome`: sealed interface { Esistente(parlanteId: ParlanteId); Nuovo(nome: String, ricorrente: Boolean) } — 'nuovo…' of Q-7
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

Sources: ADRs 0002, 0003, 0005, 0010, 0012, 0017, 0018, 0019 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S3 (+ R1, R8, R24, amendment 2026-09-24 S3 read-only in R1), manifest delta 2026-09-24-packaging (Part 3 (b), User decisions 2026-09-24).
