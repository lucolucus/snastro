---
id: "schermata-registrazioni"
type: "ui"
context: "ui"
side: "app"
wave: 8
release: "R0"
module: ":ui (snastro.ui.registrazioni)"
consumes:
  - "kernel-pl"
  - "tec-lettore-audio"
  - "tec-shell-ui"
depends_on:
  - "registrazioni-del-progetto"
  - "stati-elaborazione"
  - "servizi-registrazione"
  - "avvia-elaborazione"
  - "lettore-audio"
  - "ui-fondamenta"
  - "annulla-elaborazione"
related_adrs:
  - "0002"
  - "0003"
  - "0004"
  - "0005"
  - "0010"
  - "0012"
  - "0014"
  - "0018"
consumes_rm:
  - "registrazioni-del-progetto"
  - "stati-elaborazione"
triggers:
  - "AggiungiRegistrazione"
  - "ModificaDataRegistrazione"
  - "AvviaElaborazione"
  - "AnnullaElaborazione"
---
# schermata-registrazioni — S2 · Registrazioni del Progetto

## What to do
S2: the presenter joins the slices by registrazioneId (R1); drag-and-drop + file picker; per-row '▶' over the LettoreAudio port; live refresh via AggiornamentiVista; elapsed time from avviataAlle with an injected Clock. The Trascrizione sources are optional (R0 variant, AC-342): without them the row shows only titolo, data, durata and '▶'.

REWORK 2026-09-24 (ADR 0014): the R1 row gets a plain fillable 'Numero di persone' field next to 'Trascrivi' (NON_AVVIATA) and 'Riprova' (fallita): presenter-side validation (empty → null, 1..10 → n, else inline 'Da 1 a 10, oppure lascia vuoto' and no command, AC-375), 'Riprova' prefilled from StatiElaborazione.numeroPersone (AC-376), AvviaElaborazione invoked with (id, numeroPersone); no 'Trascrivi tutte'; no command after AggiungiRegistrazione (AC-372). AC-203/AC-344 tests updated. R0 variant (AC-342) unchanged: no field without the Trascrizione sources.

REWORK 2026-09-24 (ADR 0018): optional 'Ritrascrivi' (R2): prefilled field + validation + confirmation dialog; 'Ritrascrizione in coda/in corso' and 'Ritrascrizione non riuscita' row states; a row opens S3 iff trascrittoDisponibile; 'Annulla' on queued rows → AnnullaElaborazione (optional source, R1+). Tests AC-203 reworded, AC-448..AC-451, AC-475, AC-476.

Note: RELEASE PIVOT 2026-09-23 (user decision, dispatch.log (release-plan)): S2 ships in R0 WITHOUT Trascrizione/Parlanti features: the Trascrizione sources (stati-elaborazione, avvia-elaborazione — both already merged, compile-time only) are optional presenter inputs, absent in R0 (AC-342) and supplied by avvio-composizione in R1. The identification badge (AC-204, Parlanti read-model identificazione-registrazioni, R2) MOVED to block schermata-registrazioni-identificazione (R2) so R0 needs no R2 block. NEW SURFACE (user decision: R0 = 'import, list and play'): a per-row '▶' over the LettoreAudio port (AC-343) — not in the original ux-proposal S2, recorded as a ux amendment. Carry (stati-elaborazione code-review): NON_AVVIATA must be rendered with an action → AC-344. AMENDED 2026-09-24 (ADR 0014, user decisions 2026-09-23/24; ux-proposal.md S2 amendment 2026-09-24): no automatic start on import — every new Registrazione is NON_AVVIATA; the row carries a plain fillable 'Numero di persone' field (not a dialog) next to 'Trascrivi' (NON_AVVIATA) and 'Riprova' (fallita), validated by the presenter (AC-375), prefilled on 'Riprova' (AC-376); NO 'Trascrivi tutte' (explicit cut, user 2026-09-24: transcriptions start one row at a time). The field is state of the presenter only (not a read-model): its source for 'Riprova' is stati-elaborazione.numeroPersone. AMENDED 2026-09-24 (ADR 0018 + Amendment 2026-09-24 (b), manifest delta 2026-09-24-ritrascrivi): AzioniRegistrazioni + ritrascrivi / confermaRitrascrivi / annullaRitrascrivi (dialog) and + annullaElaborazione (queued row); RigaRegistrazione + trascrittoDisponibile, confermaRitrascrivi: Boolean, ritrascrizioneFallita: String?, annullabile. The 'Ritrascrivi' action and the AnnullaElaborazione source are OPTIONAL presenter inputs like AC-342: 'Ritrascrivi' is null in R0/R1 and supplied by avvio-parlanti (R2); AnnullaElaborazione is null in R0 and supplied by avvio-composizione (R1) and avvio-parlanti (R2). The row opens S3 iff trascrittoDisponibile. Render-check: the dialog, the three new row states and the 'Annulla' row, no overflow at the minimum window width (run-app-smoke).

### Consumes read-models: registrazioni-del-progetto, stati-elaborazione
### Triggers: AggiungiRegistrazione, ModificaDataRegistrazione, AvviaElaborazione, AnnullaElaborazione

## Tasks
- AC-199 Vuoto: 'Nessuna registrazione. Trascina qui un file audio'
- AC-200 Caricamento mostrato come indicatore
- AC-201 Errore di aggiunta mostrato inline, nessuna riga nuova
- AC-202 Ordinamento per data, dalla più recente
- AC-203 in_attesa → 'In coda (n)'; in_corso → 'In corso · separazione voci · 3:12' (tempo da avviataAlle, nessuna percentuale); fallita → motivo + campo 'Numero di persone' + 'Riprova' (solo su fallita senza Trascritto; campo precompilato, AC-376); una riga apre S3 sse trascrittoDisponibile (non sse COMPLETATA) — REWRITTEN 2026-09-24 (ADR 0014, ADR 0018)
- AC-205 La riga si aggiorna quando cambiano stato o fase
- AC-206 Modifica della data inline
- AC-342 (R0 variant) Le sorgenti di Trascrizione sono OPZIONALI: il presenter costruito SENZA StatiElaborazione e AvviaElaborazione (R0, avvio-r0) mostra per ogni riga solo titolo, data (modificabile, AC-206), durata e '▶' (AC-343); nessuna colonna di stato, nessun 'Riprova'/'Trascrivi', nessun badge, e il click su una riga non apre S3 — test del presenter con le sole finte di RegistrazioniDelProgetto, AggiungiRegistrazione, ModificaDataRegistrazione e LettoreAudio; con le sorgenti fornite (R1, avvio-composizione) valgono AC-203/AC-205/AC-344
- AC-343 (R0) Ogni riga ha '▶' che riproduce la Registrazione dall'inizio via LettoreAudio.riproduciDa(id, 0); durante la riproduzione della riga il controllo diventa pausa (StatoLettore.registrazioneId = la riga), '▶' su un'altra riga sostituisce la riproduzione in corso; LettoreAudio.disponibile(id) = false → '▶' disabilitato con 'Audio non disponibile'
- AC-344 (R1) Con le sorgenti di Trascrizione fornite, una Registrazione senza Elaborazione (StatoElaborazioneVista.NON_AVVIATA — ogni Registrazione importata, in R0 come in R1: nessun avvio automatico, ADR 0014) mostra sulla riga il campo 'Numero di persone' (vuoto = automatico) e 'Trascrivi', che invoca AvviaElaborazione(registrazioneId, numeroPersone); un errore del comando è mostrato inline sulla riga e nulla cambia; non esiste un'azione 'Trascrivi tutte' — REWRITTEN 2026-09-24 (ADR 0014)
- AC-372 (ex AC-NP5, parte S2) Una Registrazione appena aggiunta con le sorgenti di Trascrizione fornite resta NON_AVVIATA: il presenter non invoca AvviaElaborazione dopo AggiungiRegistrazione (nessuna chiamata sulla finta) e la riga mostra 'Trascrivi' con il campo vuoto
- AC-375 (ex AC-NP8) 'Trascrivi' e 'Riprova' leggono il campo 'Numero di persone' della riga: vuoto → AvviaElaborazione(id, null); 1..10 → AvviaElaborazione(id, n); altro (0, 11, testo, decimale) → messaggio inline 'Da 1 a 10, oppure lascia vuoto' e nessun comando invocato (test a tabella sul presenter)
- AC-376 (ex AC-NP9) Su una riga fallita il campo è precompilato con il numeroPersone dell'Elaborazione fallita (StatiElaborazione.numeroPersone; vuoto se assente) ed è modificabile: 'Riprova' invia il valore presente nel campo al momento del click
- AC-448 Row with a Trascritto and latest COMPLETATA, 'Ritrascrivi' supplied: it shows 'Completata', the 'Numero di persone' field prefilled with the latest numeroPersone (empty if absent), and 'Ritrascrivi'; a click opens S3; without the 'Ritrascrivi' source (R1) it shows neither the field nor the button
- AC-449 'Ritrascrivi' first validates the field exactly like AC-375 (invalid → inline 'Da 1 a 10, oppure lascia vuoto', no dialog, no command). If valid it shows the confirmation dialog: title 'Ritrascrivere «<titolo>»?'; text 'La trascrizione attuale resta consultabile finché la nuova non è pronta, poi viene sostituita. Le correzioni delle voci e le assegnazioni dei nomi di questa registrazione andranno perse.'; buttons 'Ritrascrivi' / 'Annulla'. 'Annulla' → no command, field unchanged. 'Ritrascrivi' → exactly ONE AvviaElaborazione(id, n) with the validated value; operazioneInCorso blocks a second submission; a command error is shown inline on the row (AC-344 behaviour)
- AC-450 During a re-run (Trascritto present, latest IN_ATTESA / IN_CORSO): the row shows 'Ritrascrizione in coda (n)' / 'Ritrascrizione in corso · <fase> · mm:ss' (same data as AC-203); no 'Ritrascrivi'/'Trascrivi'/'Riprova' and no field; 'Ritrascrizione in coda (n)' shows 'Annulla' (AC-475), 'Ritrascrizione in corso' does not; the row still opens S3 and the badge keeps the old counts. A row WITHOUT a Trascritto keeps the plain AC-203 labels
- AC-451 After a failed re-run (Trascritto present, latest FALLITA): the row shows 'Completata' plus the inline notice 'Ritrascrizione non riuscita: <motivo>', the field prefilled with the failed run's numeroPersone, and 'Ritrascrivi' (same dialog, AC-449); the row opens S3. A FALLITA row WITHOUT a Trascritto keeps 'Riprova', with no dialog (AC-203/AC-376 unchanged)
- AC-475 A row IN_ATTESA ('In coda (n)' or 'Ritrascrizione in coda (n)') shows 'Annulla' when the AnnullaElaborazione source is supplied; IN_CORSO, COMPLETATA, FALLITA and NON_AVVIATA rows never do; without the source (R0) no row does. A click sends exactly ONE AnnullaElaborazione(elaborazioneId of the row), with no dialog; operazioneInCorso blocks a second click
- AC-476 On Ok the row reloads to its previous state (NON_AVVIATA 'Trascrivi' with an empty field; 'Completata' + prefilled field + 'Ritrascrivi'; FALLITA 'Riprova' prefilled). On ElaborazioneGiaAvviata the row shows inline 'La trascrizione è già partita: non si può più annullare' and reloads (now 'In corso'); on ElaborazioneNonTrovata it just reloads
- (rendering — sizing/overflow/contrast/state rendering at 1280x800 and 1024x640 — is owned by realize-ui + `./gradlew :ui:renderCheck`, not a tests_nl item)

## Dependencies
- Blocks built first: `registrazioni-del-progetto` (wave 5), `stati-elaborazione` (wave 5), `servizi-registrazione` (wave 4), `avvia-elaborazione` (wave 4), `lettore-audio` (wave 7), `ui-fondamenta` (wave 6), `annulla-elaborazione` (wave 4)
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

Sources: ADRs 0002, 0003, 0004, 0005, 0010, 0012, 0014, 0018 (.mismagent/decisions/); features/trascrizione-con-parlanti/UI/ux-proposal.md S2 (+ R1, amendment 2026-09-24), ADR 0014.
