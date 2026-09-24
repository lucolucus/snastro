# Manifest delta — "Ritrascrivi" (ADR 0018, user decision 2026-09-24)
This is the authoritative input for /mismagent:build-manifest. Fold it into building-blocks.yaml and
regenerate the rich block files.

Source: ADR 0018 (decisions/0018-ritrascrivi.md), with amendments in place to ADR 0007
(enforced_by), ADR 0014 (pointer) and architecture.md (pointer).

New ACs are numbered from **AC-425**. The current max is AC-424, plus AC-155bis and AC-186bis.

Release: **R2**. The action, the Parlanti purge and the view invalidation are wired only by
`avvio-parlanti`. The Trascrizione-side reworks are release-neutral: they are built in the R2 wave
and are harmless in R1, where no composition offers the action.

## Summary of the decision (see the ADR for the rationale)
- **Command.** "Ritrascrivi" = `AvviaElaborazione(registrazioneId, numeroPersone?)` on a
  Registrazione whose latest run is `completata`. It creates a new `in_attesa` Elaborazione and keeps
  history. INV-3 is unchanged.
- **INV-4 is rewritten.** At most one open Elaborazione per Registrazione, and a new one only if none
  is open. Several `completata` are allowed.
- **Migration.** `3.sqm` drops `elaborazione_completata_unica`, and `Schema.version` becomes 4.
- **Error.** `ElaborazioneGiaCompletata` is deleted.
- **Completion replaces the Trascritto.** The completion transaction, when a Trascritto already
  exists, replaces it whole (Voci renumbered from 1). It publishes `TrascrittoSostituito(registrazioneId)`
  before `ElaborazioneCompletata`. The Parlanti synchronous subscriber then purges every
  Attribuzione and print keyed by that Registrazione's VoceRefs in the same transaction, and INV-25
  applies. A failure changes nothing.
- **S2.**
  - 'Ritrascrivi' has a 'Numero di persone' field prefilled from the latest run, and a confirmation
    dialog.
  - The row opens S3 iff a Trascritto exists.
  - Rows show "Ritrascrizione in coda/in corso" and "Ritrascrizione non riuscita" states.
- **Read-model.** `stati-elaborazione` gains `trascrittoDisponibile`.

## BOUNDARIES
- **B1 `eventi-elaborazione`** (owner eventi-pubblicati)
  - Add the pinned type
    `TrascrittoSostituito: "data class(registrazioneId: RegistrazioneId) : EventoPubblicato — published ONLY in a completion transaction that replaced an existing Trascritto, BEFORE ElaborazioneCompletata"`.
  - Consumers +`abbonato-revisione-parlanti` (SYNCHRONOUS, in-transaction) and +`avvio-parlanti`
    (after commit: AggiornamentiVistaParlanti).
  - Delivery: add "TrascrittoSostituito has one SYNCHRONOUS subscriber (Parlanti purge, ADR 0018/0012);
    the others are after commit".
  - related_adrs +0018.
- **B2 `agg-elaborazione` / `repo-trascrizione`.**
  - Remove `ElaborazioneGiaCompletata` from the pinned errors.
  - Re-pin the `ElaborazioneRepository.salva` KDoc: only `ElaborazioneGiaAperta` (another open
    Elaborazione of the same Registrazione while this one is open). Several `completata` are allowed.
  - related_adrs +0018.
- **B3 `kernel-pl` / `stati-elaborazione` view_shape.** `StatoRegistrazioneVista` gains
  `trascrittoDisponibile: Boolean`. `numVoci` is non-null iff `trascrittoDisponibile`.

## NEW BLOCKS
### persistenza-ritrascrivi (NEW, adapter/schema, `:persistenza`, wave 1, R2; related_adrs 0006, 0007, 0018)
This block owns `migrations/3.sqm`. ADR 0018's `enforced_by` is `exigible_from` this block.
- **AC-425** `migrations/3.sqm` (schema 3 → 4, forward-only) contains exactly one statement,
  `DROP INDEX elaborazione_completata_unica;`, on one line. No other table or index changes, and
  `SnastroDatabase.Schema.version` = 4. The CR-13 migration test of `:persistenza:test` stays green:
  - an empty DB migrates to the current version, passes integrity checks and runs every query;
  - `Schema.migrate` from empty equals `Schema.create`.
- **AC-426** Migration fixture test. A DB frozen at version 3, holding one `completata` Elaborazione,
  its Trascritto (Voci/Segmenti), an `attribuzione` and an `impronta_vocale` row, is migrated to 4.
  Every row is intact and `user_version` = 4. `sqlite_master` still has `elaborazione_aperta_unica`
  and `parlante_nome_attivo_unico`, and has no `elaborazione_completata_unica`.
- **AC-427** After the migration:
  - two `completata` rows for the same `registrazione_id` insert fine;
  - two rows in `in_attesa | in_corso` for the same `registrazione_id` are still refused by the
    index;
  - a `completata` next to an `in_attesa` for the same Registrazione is accepted.
- `persistenza-schema` **AC-9** REWORDED: "The two partial unique indexes of ADR 0007 still in force
  (`elaborazione_aperta_unica`, `parlante_nome_attivo_unico`) exist, each on one line (presence rule
  green). `elaborazione_completata_unica` was dropped by 3.sqm (ADR 0018, AC-425)."

### sostituzione-trascritto-policy (NEW, application-service, `:parlanti:applicazione (..politiche)`, wave 4, R2; related_adrs 0009, 0012, 0018; invariants INV-15, INV-25)
Policy `ApplicaSostituzioneTrascrittoPolitica(parlanti: ParlanteRepository, attribuzioni: AttribuzioneRepository)`,
`fun applica(registrazioneId: RegistrazioneId): Esito<Unit>`. It is structural only (ADR 0012 (b)
enforced_by), goes through the aggregates (RC-1), and needs no new port.
- **AC-428** After `applica(r)` there are zero Attribuzioni and zero print rows keyed by any VoceRef
  of `r`, for every Parlante, `attivo` or `eliminato`. This includes a print row of `r` with no
  Attribuzione (defensive, via `impronteDiRegistrazione`). Attribuzioni and prints of any other
  Registrazione are untouched.
  - Test fakes: 2 Registrazioni, 3 Parlanti.
  - Count check: rows for the other Registrazione are identical before and after.
- **AC-429** INV-25 after the purge:
  - an `attivo occasionale` whose only Attribuzioni were in `r` no longer exists (`rimuovi`);
  - an `attivo occasionale` that also has an Attribuzione in another Registrazione is kept, with that
    print;
  - a `ricorrente` is kept with the same Nome, tipo and stato and its prints of other Registrazioni,
    even when left with zero Attribuzioni and zero prints;
  - an `eliminato` tombstone is kept (still `eliminato`, same Nome), and its Attribuzione in `r` is
    gone.
- **AC-430** Idempotent and cheap. `applica` twice gives the same state as once. On a Registrazione
  with no Attribuzione and no print it returns `Ok` and does no `salva`/`rimuovi` (counting fakes).
  The fakes `EstrattoreImprontaFinta` and `DecodificatoreAudioFinta` are never invoked.
- **AC-431** An `Esito.Errore` from `ParlanteRepository.salva` is returned unchanged. Nothing is
  swallowed, so the caller's transaction rolls back.

## REWORKED BLOCKS
### elaborazione (aggregate, `:trascrizione:dominio`)
- **AC-432** `ErroreTrascrizione` no longer has `ElaborazioneGiaCompletata`. Test:
  `! grep -rn 'ElaborazioneGiaCompletata' --include='*.kt' --exclude-dir=build .` exits 0 once every
  block below is merged. The block's own gate is the sealed hierarchy compiling without it. INV-3
  tests are unchanged and green: `completata` and `fallita` stay terminal.
- notes: "AMENDED 2026-09-24 (ADR 0018): no change to the state machine; Ritrascrivi = a NEW Elaborazione."

### porte-trascrizione (port + testFixtures: `ElaborazioneRepositoryContratto`, `ElaborazioneRepositoryFinta`)
- **AC-433** The contract, run against the fake and later against SQL:
  - two `completata` Elaborazioni of the same Registrazione are both saved, and `diRegistrazione`
    returns both;
  - `salva` of an open Elaborazione while another of the same Registrazione is open →
    `Errore(ElaborazioneGiaAperta)`, store unchanged;
  - an open one next to one or more `completata` → `Ok`.

  The fake honours exactly this, with no `GiaCompletata` branch.

### avvia-elaborazione (application-service)
- invariants: INV-4 REWRITTEN as "per Registrazione at most one Elaborazione in_attesa|in_corso; a
  new one iff none is open (after none, fallita, or completata = Ritrascrivi); several completata
  allowed (history)".
- REMOVE the test_nl "INV-4 dopo una completata → ElaborazioneGiaCompletata (nessuna ri-elaborazione)".
- **AC-434** (Ritrascrivi) After a `completata`, `AvviaElaborazione(r, 3)` creates a new `in_attesa`
  with `numeroPersone` 3. The `completata` row is unchanged (same state, same numeroPersone), and so
  is the Trascritto: same Voci, Segmenti and counters, and no `TrascrittoRepository.salva` call
  (counting fake).
- **AC-435** With `completata` + `in_attesa` (a Ritrascrizione queued), or with `completata` +
  `in_corso`, another `AvviaElaborazione` → `Errore(ElaborazioneGiaAperta)`, and no new row.
- **AC-436** History:
  - `completata`, then a `fallita` re-run, then `AvviaElaborazione` → Ok. There are 3 rows, and the
    first two are unchanged.
  - `completata`, `completata`, then `AvviaElaborazione` → Ok.
  - `NumeroPersoneFuoriIntervallo` still wins before any write (AC-369).
- notes: "AMENDED 2026-09-24 (ADR 0018): the only pre-check is 'any open → ElaborazioneGiaAperta'."

### esegui-elaborazione (application-service)
- invariants: INV-5 REWORDED as "a Trascritto exists iff the Registrazione has ≥ 1 completata; written
  (created or REPLACED whole) atomically with EACH transition to completata; never touched by any
  other outcome".
- consumes +`eventi-elaborazione` (TrascrittoSostituito).
- Remove the `MOTIVO_ELABORAZIONE_GIA_COMPLETATA` branch. The exhaustive `when` loses that variant.
- **AC-437** A first completion (no Trascritto before) publishes `ElaborazioneCompletata` and never
  `TrascrittoSostituito` (recording dispatcher fake).
- **AC-438** Completion over an existing Trascritto, in ONE transaction (fake `UnitaDiLavoro` records
  a single `inTransazione`):
  - the Elaborazione becomes `completata`;
  - the Trascritto is replaced whole by the new one: Voci numbered from 1 by first appearance (AC-71),
    counters from the new `Trascritto.crea`, and no Voce or Segmento of the old one left;
  - `TrascrittoSostituito(r)` is published, strictly BEFORE `ElaborazioneCompletata(r)`;
  - the earlier `completata` row stays `completata`.
- **AC-439** The old Trascritto stays readable until then. While the pipeline is inside
  `Diarizzatore.diarizza` (fake blocked on a latch), `TrascrittoRepository.trova(r)` returns the old
  Voci and Segmenti unchanged, and no transaction is open (AC-73).
- **AC-440** A re-run ending `fallita` publishes no `TrascrittoSostituito`, and the old Trascritto is
  unchanged (equal before and after). The previous `completata` stays `completata`. This must hold for
  each of:
  - a fault in any phase;
  - "nessun parlato rilevato" (AC-72/AC-386);
  - `Trascritto.crea` refusing its input;
  - `RecuperaElaborazioniInterrotte` → "interrotta".
- **AC-441** A synchronous subscriber that answers `TrascrittoSostituito` with `Esito.Errore` rolls
  back the whole completion. The old Trascritto is intact and no `ElaborazioneCompletata` reaches
  after-commit subscribers. The compensation marks the run `fallita` with "salvataggio del risultato
  non riuscito" (existing F2 path).

### eventi-pubblicati (owner of eventi-elaborazione)
- **AC-442** `snastro.trascrizione.applicazione.eventi.TrascrittoSostituito(registrazioneId: RegistrazioneId) : EventoPubblicato`
  exists, with no other field.
  - `DispatcherEventiInMemoria` delivers it to synchronous subscribers inside the publishing
    transaction, and to after-commit subscribers only after COMMIT, never on rollback.
  - Test with a synchronous and an after-commit recording subscriber on a rolled-back and on a
    committed transaction.

### repository-sql-trascrizione (adapter)
- **AC-111** REWRITTEN: "A violation of `elaborazione_aperta_unica` becomes `ElaborazioneGiaAperta`,
  never a raw exception. There is no other constraint mapping (`elaborazione_completata_unica` no
  longer exists, ADR 0018)."
- **AC-443** Round-trip: two `completata` Elaborazioni of the same Registrazione are both returned
  by `diRegistrazione`. `ElaborazioneRepositoryContratto` (AC-433) passes against SQL.
- **AC-444** `TrascrittoRepositorySql.salva` of a NEW Trascritto over an existing one leaves only the
  new state (round-trip):
  - the old one has Voci 1..5 and counters 6/40; the new one has Voci 1..3 and counters 4/20;
  - after the save there are exactly the new Voci, Segmenti and counters, and no row of the old one.
- **AC-445** Deferred-FK backstop, documented by test, on a real SQLite `UnitaDiLavoroSql`:
  - replacement in one transaction with an `attribuzione` on old Voce 5 (absent from the new
    Trascritto) and NO purge → the COMMIT fails, and the old Trascritto and the attribuzione are
    intact afterwards;
  - the same transaction with the rows of Voce 5 deleted first → commits.

### abbonato-revisione-parlanti (adapter, synchronous subscriber)
- consumes +`eventi-elaborazione`; depends_on +`sostituzione-trascritto-policy`.
- **AC-446** `TrascrittoSostituito(r)` → `ApplicaSostituzioneTrascrittoPolitica.applica(r)` inside
  the publishing transaction. Its `Esito.Errore` dooms and rolls back the transaction (test on
  `DispatcherEventiInMemoria` with a fake `UnitaDiLavoro`). `ElaborazioneCompletata` and every other
  event → no call.

### stati-elaborazione (read-model)
- view_shape +`trascrittoDisponibile: Boolean`.
- AC-165 REWORDED: "numVoci = the Trascritto's Voci count whenever a Trascritto exists, `null`
  otherwise".
- **AC-447** Table test. `stato`, `posizioneInCoda`, `fase`, `motivoFallimento` and `numeroPersone`
  always come from the LATEST Elaborazione (AC-162 unchanged). The history column lists the
  Elaborazioni in creation order.

  | history | stato | trascrittoDisponibile | numVoci |
  |---|---|---|---|
  | completata | COMPLETATA | true | of the Trascritto |
  | completata, in_attesa | IN_ATTESA (posizione n) | true | old count |
  | completata, in_corso | IN_CORSO (fase) | true | old count |
  | completata, fallita | FALLITA (motivo) | true | old count |
  | fallita | FALLITA | false | null |
  | completata, completata | COMPLETATA | true | new count |
  | none | NON_AVVIATA | false | — |

### schermata-registrazioni (ui, S2)
- AzioniRegistrazioni:
  - +`ritrascrivi: (RegistrazioneId) -> Unit` (asks for confirmation, after validation);
  - +`confermaRitrascrivi: (RegistrazioneId) -> Unit`;
  - +`annullaRitrascrivi: (RegistrazioneId) -> Unit`.
- The presenter gets 'Ritrascrivi' only if it is supplied, like AC-342's optional sources. It is `null`
  in R0/R1, and supplied by avvio-parlanti in R2.
- `RigaRegistrazione`:
  - +`trascrittoDisponibile`;
  - +`confermaRitrascrivi: Boolean` (dialog shown);
  - +`ritrascrizioneFallita: String?` (motivo).
- AC-203 and the `apriRiga` rule REWORDED: "a row opens S3 iff trascrittoDisponibile (not iff
  COMPLETATA)".
- **AC-448** Row with a Trascritto and latest COMPLETATA, 'Ritrascrivi' supplied:
  - it shows 'Completata', the 'Numero di persone' field prefilled with the latest `numeroPersone`
    (empty if absent), and 'Ritrascrivi';
  - a click opens S3;
  - without the 'Ritrascrivi' source (R1), it shows neither the field nor the button.
- **AC-449** 'Ritrascrivi' first validates the field exactly like AC-375 (invalid → inline 'Da 1 a
  10, oppure lascia vuoto', no dialog, no command). If the field is valid, it shows the confirmation
  dialog:
  - title "Ritrascrivere «<titolo>»?";
  - text "La trascrizione attuale resta consultabile finché la nuova non è pronta, poi viene
    sostituita. Le correzioni delle voci e le assegnazioni dei nomi di questa registrazione andranno
    perse.";
  - buttons 'Ritrascrivi' / 'Annulla'.

  'Annulla' → no command, field unchanged. 'Ritrascrivi' → exactly ONE
  `AvviaElaborazione(id, n)`, where n is the validated field value. `operazioneInCorso` blocks a
  second submission. A command error is shown inline on the row (AC-344 behaviour).
- **AC-450** During a re-run (Trascritto present, latest IN_ATTESA / IN_CORSO):
  - the row shows 'Ritrascrizione in coda (n)' / 'Ritrascrizione in corso · <fase> · mm:ss' (same
    data as AC-203);
  - no 'Ritrascrivi'/'Trascrivi'/'Riprova' and no field;
  - the row still opens S3, and the badge keeps the old counts.
  - A row WITHOUT a Trascritto keeps the plain AC-203 labels ('In coda (n)', 'In corso · …').
- **AC-451** After a failed re-run (Trascritto present, latest FALLITA):
  - the row shows 'Completata' plus the inline notice 'Ritrascrizione non riuscita: <motivo>', the
    field prefilled with the failed run's `numeroPersone`, and 'Ritrascrivi' (same dialog, AC-449);
  - the row opens S3.
  - A FALLITA row WITHOUT a Trascritto keeps 'Riprova', with no dialog (AC-203/AC-376 unchanged).
- Render-check: the dialog and the three new row states in the render proof (run-app-smoke), with no
  overflow at the minimum window width.

### schermata-registrazione (ui, S3 read-only R1 part)
- Consumes +`stati-elaborazione` (state of the shown Registrazione). This is an optional source, and
  without it no banner is shown.
- **AC-452** While the latest Elaborazione of the shown Registrazione is `in_attesa` / `in_corso` and
  a Trascritto exists, S3 shows the banner "Ritrascrizione in corso: questa trascrizione sarà
  sostituita quando la nuova sarà pronta.". It disappears on the next `Cambiamento` that shows another
  state. The transcript stays readable and playable.
- **AC-453** On the `Cambiamento` after a replacement, S3 reloads:
  - the new Segmenti and 'Voce n' labels are shown;
  - any selection is cleared;
  - playback of an old Segmento stops (no stale Segmento id is kept).

  Presenter test with a fake view source swapped between two generations.

### schermata-registrazione-identificazione (ui, S3 R2 panel)
- **AC-454** During a re-run the banner of AC-452 adds "Le correzioni e i nomi assegnati andranno
  persi.". Revisione and naming stay enabled (ADR 0018 §4).
- **AC-455** A pending Conferma or Salta on an old VoceRef that resolves after the replacement shows
  AC-318 VoceCambiata, or the Voce-not-found message, in plain words, never a crash. The panel then
  shows the new Voci, all 'da identificare'.

### ui-fondamenta (MessaggiErrore)
- AC-180 still holds. Remove the `ElaborazioneGiaCompletata` branch, since the variant no longer
  exists (AC-432). The `MessaggiErroreTest` case goes with it.

### avvio-parlanti (R2 composition) — AggiornamentiVistaParlanti + S2 wiring
- consumes +`eventi-elaborazione`.
- **AC-456** `TrascrittoSostituito` after commit → `ProposteSerializzate.invalidaTutte()` and ONE
  `Cambiamento(null)`. It is never delivered on rollback.
- **AC-457** The R2 composition supplies 'Ritrascrivi' to the S2 presenter, and registers
  `AbbonatoRevisioneParlanti` (with the sostituzione policy) BEFORE the first command. Test: the
  subscriber is registered before `CodaElaborazioni` starts.
- **AC-458** E2E on `databaseInMemoria` with fake ML ports.
  - Setup:
    - Registrazione `completata`;
    - Voce 1 → `ricorrente` "Mario", with a print, and Mario also attributed in another
      Registrazione;
    - Voce 2 → `occasionale` "Ospite del 12/09/2026", with a print, and nowhere else.
  - Action: Ritrascrivi with 2 persons, and the fake pipeline completes with 3 Voci.
  - Expected:
    - zero `attribuzione` and zero `impronta_vocale` rows for that Registrazione;
    - Mario exists with his other Attribuzione and print; the Ospite no longer exists;
    - `trascritto-view` shows the 3 new Voci;
    - the Documento file is rewritten with only 'Voce 1..3' labels and the new texts;
    - the S2 row is 'Completata' with the badge "3 voci · 3 da identificare".
- **AC-459** E2E failure, same setup: the fake pipeline fails in diarization. The `attribuzione`
  and `impronta_vocale` rows, the Trascritto, the Documento file bytes and the Galleria are
  unchanged. S2 shows 'Ritrascrizione non riuscita: errore nella separazione delle voci'.

### avvio-composizione (R1 composition)
- **AC-460** The R1 composition does NOT supply 'Ritrascrivi' to S2: a `completata` row shows no
  field and no button (AC-448, second part). It registers no Parlanti subscriber (unchanged).

## Not changed
- **abbonato-documento / rigenerazione-documento.** `ElaborazioneCompletata` already triggers the
  after-commit Rigenerazione (AC-186). After the purge, INV-24 renders 'Voce n'. The e2e AC-458
  covers it. There is no subscription to `TrascrittoSostituito`.
- **riallinea-impronte / abbonato-riallineamento-impronte.** The purged rows are gone, and the
  compare-and-set UPDATE never inserts (ADR 0012 (b)).
- **revisione, trascritto (aggregate).** The replacement is a fresh `Trascritto.crea` saved over
  the old one. INV-12 applies per generation.

## Texts to fold by their owners (not written by the architect)
- **tactical-model.md § Trascrizione** (tactical-modeler / build-manifest):
  - INV-4 and INV-5: the texts above.
  - INV-12: add "per Trascritto generation: a replacement (ADR 0018) renumbers from 1; every
    VoceRef-keyed Parlanti row of the old generation is purged in the same transaction".
  - Commands: `AvviaElaborazione` actor adds "'Ritrascrivi' on a completata one, prefilled with the
    latest numeroPersone".
  - Domain events: +`TrascrittoSostituito (registrazioneId)` → Parlanti sostituzione-policy
    (in-transaction) + `AggiornamentiVista`/Proposta invalidation.
- **tactical-model.md § Parlanti:**
  - Policy: +"on `TrascrittoSostituito` → purge every Attribuzione and ImprontaVocale of the
    Registrazione, then [INV-25]" → application-service block `sostituzione-trascritto-policy`.
  - [INV-21]/[INV-17] untouched.
- **context-map.md § Trascrizione, ubiquitous language** (analyst):
  - `Elaborazione`: replace "v1: at most ONE completed `Elaborazione` per `Registrazione` —
    re-running it on a reviewed recording is not offered (retry only after `fallita`)" with "several
    may be `completata` (history); the `Trascritto` is the result of the latest one. A new one can
    start whenever none is open: 'Trascrivi', 'Riprova' after `fallita`, 'Ritrascrivi' after
    `completata` [user 2026-09-24, ADR 0018]".
  - Add `Ritrascrivere` / "Ritrascrivi" = starting a new `Elaborazione` on a `Registrazione` that
    already has a `Trascritto`. The old `Trascritto` stays until the new run completes, and is then
    replaced whole, with the names of its Voci lost. Not: rielaborare, rifare, reset.
- **UI/ux-proposal.md S2 + S3** (ux-designer): the S2 states and dialog of AC-448..451, and the S3
  banner of AC-452/454.
