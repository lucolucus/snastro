---
scope: global
status: accepted
supersedes: null   # partial, amended in place with pointers here: ADR 0007 (index elaborazione_completata_unica, INV-4 "at most one completata"); tactical INV-4 "no re-run after completata" / INV-5 wording; ADR 0014 (where the Numero di persone field is offered)
closes_spike: null
enforced_by:
  kind: presence+prohibition
  rule: "grep -qE '^DROP INDEX elaborazione_completata_unica;$' persistenza/src/main/sqldelight/migrations/3.sqm && ! grep -rnE --include='*.kt' --exclude-dir=build '(attribuzioneQueries|improntaVocaleQueries|parlanteQueries)' trascrizione | grep -q ."
  exigible_from: "persistenza-ritrascrivi"
---
# 0018 — "Ritrascrivi": a new Elaborazione over a completata one; the Trascritto is replaced atomically when it completes, and the Parlanti data keyed by the old Voci is purged in the same transaction

## Context
**User decision (2026-09-24, dispatch.log `(r2) decision`):** the user must be able to transcribe
again a `Registrazione` whose `Elaborazione` is already `completata`. The reason is that diarization
quality will change: new embedding models or clustering are being tried on Via Roquel right now
(fix-batch-18). Users will need to re-run recordings they already transcribed, and may want to retry
with another "Numero di persone" (ADR 0014 Amendment 2026-09-24: `k` is very sensitive).

Today this is impossible, and the rule is enforced at three levels:
- The UI offers "Riprova" only on `fallita`.
- The domain rule [INV-4] ("no re-run after `completata`") is pre-checked by `AvviaElaborazioneServizio`,
  which returns `ElaborazioneGiaCompletata`.
- The store has the partial unique index `elaborazione_completata_unica` (ADR 0007).

What makes it more than lifting a guard:
- **`VoceRef = (registrazioneId, voceId)` keys Parlanti data.** It keys `Attribuzione`
  (`PRIMARY KEY`) and `ImprontaVocale` rows. `voceId` IS the "Voce n" number (R7), assigned by first
  appearance (R24) and fixed for the life of the `Trascritto` ([INV-12]). A new diarization has **no
  relation** to the old numbering: new Voce 2 is not old Voce 2.
- **The FKs from `attribuzione`/`impronta_vocale` to `voce` are `DEFERRABLE INITIALLY DEFERRED`**
  (`1.sqm`), because `TrascrittoRepositorySql.salva` deletes and re-inserts every `voce` row.
  Replacing a Trascritto without purging the Parlanti rows therefore has two outcomes. If a
  number is missing from the new Trascritto, the COMMIT fails. If it is present, the old attribution
  **silently re-attaches** to an unrelated new Voce, and a biometric print gets pinned to the wrong
  person. The second outcome is the dangerous one.
- **Contexts cannot reach into each other.** Trascrizione may not touch Parlanti tables (ADR 0002 edges,
  ADR 0006 per-context adapters). Invariant-carrying cross-context reactions run as **synchronous
  subscribers inside the publishing transaction** (ADR 0012), and they never decode or extract
  (ADR 0012 (b) `enforced_by`).

## Decision

### 1. Domain model: a NEW `Elaborazione`, history kept; `completata` stays terminal
- "Ritrascrivi" is the same command as "Trascrivi"/"Riprova": **`AvviaElaborazione(registrazioneId,
  numeroPersone?)`**. It creates a **new** `Elaborazione` `in_attesa`, which goes through the normal
  FIFO queue (ADR 0004). No reset, no new state, and **no change to INV-3**: `completata` and `fallita`
  stay terminal. Earlier `Elaborazione`s, `completata` ones included, are kept as history.
- **[INV-4] is rewritten** (the tactical model must fold this text):
  > [INV-4] per `Registrazione`, at most one `Elaborazione` is `in_attesa | in_corso`. A new
  > `Elaborazione` can be started iff none is open, whatever the earlier ones ended in (none,
  > `fallita`, or `completata` = "Ritrascrivi") [user 2026-09-24]. Several `completata` may exist.
- **The store.** `elaborazione_aperta_unica` stays unchanged (ADR 0007). **`elaborazione_completata_unica`
  is dropped** by the forward-only migration **`migrations/3.sqm`** (schema 3 → 4, ADR 0006 (a)), whose
  only statement is `DROP INDEX elaborazione_completata_unica;`. Existing rows are untouched, and the
  baseline `Schema.version` becomes 4.
- **`ElaborazioneGiaCompletata` is deleted** from `ErroreTrascrizione`, because no path can produce it
  any more. Its uses go with it: the service pre-check, the SQL constraint mapping, the contract/fake,
  the pipeline's `motivo` table, and `MessaggiErrore` in `:ui`.
- **Which `Elaborazione` is "current".** This needs no new column and no pointer. Two facts make it
  derivable:
  - `elaborazione_aperta_unica` means the runs of one `Registrazione` are strictly sequential, so
    creation order equals completion order.
  - The *latest* `Elaborazione` by `(creataAlle, id)` is already the one S2 shows (AC-162).

  Its `numeroPersone` is what "Riprova"/"Ritrascrivi" prefill. **The Trascritto is always the
  result of the most recent `completata`**, because each completion rewrites it (point 2). No reader
  needs to know *which* `completata` row produced the Trascritto. Only these two facts are needed:
  "is there a Trascritto" and "what is the latest run". *(Accepted edge: if the system clock moves
  backwards between two runs, the ordering by `creataAlle` can pick the older row, and the only effect
  is a wrong prefill. No data is affected.)*
- **[INV-5] is reworded:**
  > [INV-5] a `Trascritto` exists iff its `Registrazione` has at least one `completata`
  > `Elaborazione`. It is written (created, or **replaced whole**) atomically with **each** transition
  > to `completata`, and is never touched by any other outcome of a run.
- **[INV-12] is scoped per Trascritto *generation*.** A replacement is a fresh `Trascritto.crea`:
  Voci are renumbered from 1 by first appearance (R24/AC-71), and the counters restart. Numbers are
  never reused *within* one generation. Across generations they are reused **on purpose**, so the user
  sees "Voce 1…k" again. This is safe only because point 3 purges every row keyed by an old
  `VoceRef` in the same transaction.

### 2. The old `Trascritto`: replaced atomically on completion, never earlier
- `AvviaElaborazione` never touches the Trascritto. While the re-run is queued or running, the old
  Trascritto (Voci, Segmenti, Revisioni applied) remains the source of truth. It stays readable in S3,
  and its `Documento` stays on disk.
- The **completion transaction** of `EseguiProssimaElaborazioneServizio` (ADR 0012, the one already
  writing `completata` + `Trascritto`) re-reads the existing Trascritto **inside** that transaction.
  If one exists, it does all of the following in the same transaction:
  1. mark the `Elaborazione` `completata`;
  2. replace the Trascritto whole with the new one, through `TrascrittoRepository.salva` (it already
     rewrites counters and deletes and re-inserts `voce`/`segmento`);
  3. publish **`TrascrittoSostituito(registrazioneId)`**. Its synchronous Parlanti subscriber
     purges (point 3) before the COMMIT;
  4. publish `ElaborazioneCompletata(registrazioneId)` as today.

  A first completion (no Trascritto before) publishes **no** `TrascrittoSostituito`.
- Everything else is unchanged from ADR 0012 / AC-68..75. The pipeline runs outside any transaction.
  The completion transaction re-reads the `Elaborazione` by id. A refused or throwing completion
  (a subscriber's `Esito.Errore` included) rolls back and is compensated to `fallita` (see point 6).

### 3. Parlanti data tied to the old Voci: purged at replacement, in the same transaction
- **Rule.** When a Trascritto is replaced, every `Attribuzione` and every `impronta_vocale` row keyed
  by a `VoceRef` of that `Registrazione` is deleted. This applies to every `Parlante`, `attivo` and
  `eliminato` alike. No mapping from old to new Voci is attempted: the numbering has no relation,
  and a heuristic overlap match would pin prints to the wrong person.
  - The user names the new Voci again.
  - Once the extractor exists (R2, spike `impronta-vocale-affidabilita`), the `Proposta` will propose
    the known `Parlante`s again from their prints in *other* Registrazioni.
- **Where.** A new policy, **`ApplicaSostituzioneTrascrittoPolitica`**, in `:parlanti:applicazione`
  `..politiche`. It is invoked by the existing synchronous subscriber `AbbonatoRevisioneParlanti`
  (`:parlanti:adattatori`), which gains the translation `TrascrittoSostituito → applica(registrazioneId)`.
  That subscriber is already registered by the R2 composition before the first command.
  - The policy is **structural only**. It never decodes or extracts, so ADR 0012 (b)'s `enforced_by`
    already covers it.
  - It goes through the aggregates (RC-1): `AttribuzioneRepository.diRegistrazione` + `rimuovi`, and
    `Parlante.rimuoviImpronta` + `ParlanteRepository.salva`.
  - It also removes any print row of the Registrazione found by `impronteDiRegistrazione` that has
    no Attribuzione. [INV-15] says such a row cannot exist, and deleting it anyway is defensive: no
    row keyed by an old `VoceRef` may survive into the new generation.
  - Prints are removed by the same deletion as every other removal path (ADR 0009, `secure_delete=ON`).
- **Parlanti left without any `Attribuzione`: [INV-25] applies unchanged.**
  - An `attivo` `occasionale` whose only Attribuzioni were in this Registrazione **ceases to exist**
    (`rimuovi`). Nothing references it any more, and it has zero prints.
  - A `ricorrente` is **kept as it is**: Nome, tipo, and its prints from other Registrazioni. It is
    kept even with zero Attribuzioni and zero prints.
  - An `eliminato` tombstone is kept. Only its Attribuzione in this Registrazione goes.
  - ~~*(To confirm with the user. They wrote "any Parlante left with no attribution stays as it is (INV
    rules)", and this reads it as "per the INV rules", i.e. [INV-25].)*~~ **Confirmed by the user,
    see Amendment 2026-09-24 (b) §1.**
- **Mechanical boundary.** Trascrizione never touches Parlanti tables. The purge lives in Parlanti,
  reached only through the published event (`enforced_by`, second clause). If the synchronous
  subscriber is missing, the deferred FKs still backstop one of the two outcomes: a missing number
  fails the COMMIT. They cannot catch the silent re-attach. So **"Ritrascrivi" is offered only by a
  composition that registers the subscriber** (R2 `avvio-parlanti`, point 4), and a composition e2e
  test proves the purge.

### 4. UI
- **S2 row, Trascritto present, latest run `completata`.** The row shows the "Completata" state and
  opens S3 as today. It also shows the **"Numero di persone"** field and a **"Ritrascrivi"** button.
  - The field follows the ADR 0014 rules unchanged: a plain field on the row, empty means automatic,
    1..10, and invalid input gives the inline "Da 1 a 10, oppure lascia vuoto" with no command. The
    hint asks for the real count, never an upper bound (ADR 0014 Amendment 2026-09-24).
  - The field is **prefilled** with the latest run's `numeroPersone`, exactly like "Riprova".
  - "Ritrascrivi" is offered only when the S2 presenter is given the action, which is R2 composition
    only (point 3). R0/R1 compositions show no "Ritrascrivi".
- **Confirmation** (a destructive action, so a dialog: the only S2 action with one). Title:
  "Ritrascrivere «<titolo>»?". Text: "La trascrizione attuale resta consultabile finché la nuova non è
  pronta, poi viene sostituita. **Le correzioni delle voci e le assegnazioni dei nomi di questa
  registrazione andranno perse.**" Buttons: "Ritrascrivi" / "Annulla".
  - "Annulla" sends no command and keeps the field.
  - Validation of the field happens **before** the dialog.
  - Revisioni (unisci/dividi/riassegna) are lost too, and the text says so. The user's brief named
    only the names.
- **S2 while re-running** (Trascritto present, latest `in_attesa` / `in_corso`):
  - The row shows "Ritrascrizione in coda (n)" / "Ritrascrizione in corso · <fase> · mm:ss". These
    are the same data as AC-203, with a label that makes clear the transcript being shown is the
    current one.
  - There is no "Ritrascrivi"/"Riprova" (INV-4: already open).
  - **The row still opens S3** on the old transcript, and the badge still shows the old counts.
- **S2 after a failed re-run** (Trascritto present, latest `fallita`):
  - The row shows "Completata". It opens S3 on the unchanged transcript.
  - It also shows an inline notice "Ritrascrizione non riuscita: <motivo>", the field prefilled with
    the failed run's value, and "Ritrascrivi" (same dialog).
  - A `fallita` **without** a Trascritto keeps "Riprova" with no dialog (AC-203 unchanged): nothing
    would be lost.
- **Row opening rule.** A row opens S3 **iff a Trascritto exists**. This replaces the rule "iff
  `COMPLETATA`".
- **Read-model (consumer-driven, S2).** `stati-elaborazione`'s `StatoRegistrazioneVista` gains
  **`trascrittoDisponibile: Boolean`**. `numVoci` becomes non-null **whenever a Trascritto exists**,
  whatever the latest run's state. `stato`/`fase`/`motivoFallimento`/`posizioneInCoda`/`numeroPersone`
  keep coming from the latest `Elaborazione`. No new enum value is added: the S2 presenter derives
  the "Ritrascrizione …" labels from the pair.
- **S3 during a re-run.**
  - ~~S3 stays **fully usable**: reading, playing and, in R2, Revisione and naming. If the re-run fails,
    that work is kept.~~ **Superseded by Amendment 2026-09-24 (b) §2: S3 is READ-ONLY while a re-run is
    queued or running.**
  - A banner warns: "Ritrascrizione in corso: questa trascrizione sarà sostituita quando la nuova sarà
    pronta." R2 adds: "Le correzioni e i nomi assegnati andranno persi."
  - After the replacement, S3 reloads on the view refresh. The selection is reset, and pending S3
    commands on old `VoceRef`s resolve as `VoceCambiata` / `VoceNonTrovata` in plain words.
  - ~~*(Alternative, for the user: make S3 read-only while a re-run is open.)*~~ Chosen by the user:
    Amendment 2026-09-24 (b) §2.

### 5. Events, Documento, view refresh
- **New published event** (boundary `eventi-elaborazione`, `snastro.trascrizione.applicazione.eventi`,
  Published Language): **`TrascrittoSostituito(registrazioneId: RegistrazioneId) : EventoPubblicato`**.
  - It is published only in a completion transaction that replaced an existing Trascritto, and before
    `ElaborazioneCompletata`.
  - The payload is the id only, because the subscriber reads what it purges from its own repositories.
  - Consumers:
    - Parlanti **synchronous** policy (point 3);
    - `AggiornamentiVistaParlanti` **after commit**. It calls `ProposteSerializzate.invalidaTutte()`
      (cached Proposte are keyed by `VoceRef` and now point at the wrong Voci) and emits
      `Cambiamento(null)`. The Galleria counts of any `Parlante` may have changed, and an
      `occasionale` may be gone.
- **Documento.** It needs no new subscription. `ElaborazioneCompletata` already triggers the
  after-commit, coalesced, retried `Rigenerazione` (AC-186). After the purge every Voce is unattributed,
  so [INV-24] renders "Voce n" for all of them, over the new Segmenti. The file name (date + titolo)
  is unchanged, so the file is overwritten in place. Until the commit the old `.md` stays as it was.
- **`AggiornamentiVista` (Trascrizione half).** It needs no change: `ElaborazioneAvviata`/`Completata`/
  `Fallita` already emit `Cambiamento(registrazioneId)`, which refreshes S2 and S3.
- **`RiallineaImpronte`.** It needs nothing either. The purged rows no longer exist, and its
  compare-and-set UPDATE never INSERTs (ADR 0012 (b)), so an in-flight run can resurrect nothing.

### 6. Failure: the old result is untouched
- A re-run that ends `fallita` leaves the old Trascritto, the Attribuzioni, the prints and the
  `Documento` file **byte-for-byte unchanged**, and publishes no `TrascrittoSostituito`. This covers
  any pipeline phase, "nessun parlato rilevato", `Trascritto.crea` refusing its input, and
  `RecuperaElaborazioniInterrotte` marking it "interrotta".
- The previous `completata` row stays `completata`, and S2 shows point 4's "failed re-run" state.
- If the **completion transaction** itself fails, it rolls back whole: the Trascritto write, the
  Parlanti purge and the state change all go. This includes a synchronous subscriber returning
  `Esito.Errore`, and the store throwing. The existing compensation then marks the run `fallita`
  ("salvataggio del risultato non riuscito"). The old data is intact, for the same reason.

## Rejected options
- **Reset**, meaning delete the old `Elaborazione` and Trascritto at "Ritrascrivi". This loses the
  transcript during the whole re-run, and loses everything if the re-run fails. The user ruled that
  out ("never earlier").
- **A new state `sostituita` for the old `completata`.** This breaks INV-3 (terminal states) for no
  reader: nothing needs to tell "current" from "past" completed runs beyond point 1's derivation.
- **A `trascritto.elaborazione_id` pointer.** It is a rebuild of a table referenced by `voce` (a
  SQLite table rebuild under FKs), for information no reader needs.
- **Carrying Attribuzioni over by time overlap** (old Voce ↔ new Voce with the largest shared speech).
  It is heuristic, and a wrong carry-over pins a biometric print to the wrong person, which ADR 0009's
  privacy stance cannot accept. The `Proposta` is the designed way to re-suggest known speakers.
- **Keeping Voce numbering monotonic across generations** (new Voci start at `prossimaVoce`, so no
  `VoceRef` is ever reused). It is safer against stale references, but the user would see "Voce 7…12"
  on a fresh transcript. It is not needed because point 3 purges in the same transaction. The residual
  race it would close is accepted below.
- **Purging from Trascrizione** (a trigger or `ON DELETE CASCADE` on `voce`). `salva` deletes and
  re-inserts `voce` on **every** Revisione, so a cascade would wipe attributions on every Revisione.
  Trascrizione may not touch Parlanti tables either.

## Consequences
- ~~**Accepted residual race.**~~ *(Closed for S3 by Amendment 2026-09-24 (b) §2; text kept for
  history.)* A Revisione or `ConfermaAttribuzione` fired from a stale S3 in the
  milliseconds between the replacement COMMIT and S3's reload runs on the **new** generation, with an
  old `voceId`.
  - `ConfermaAttribuzione`/`SaltaVoce` re-read the Voce in their transaction and compare
    `SorgenteImpronta` (ADR 0012 (b)). A different Voce gives `VoceCambiata`, and nothing is written.
    An identical source means identical audio, so the attribution is still right.
  - A Revisione (`unire`/`dividere`/`riassegnare`) has no such guard. It may act on the new Voce with
    that number, and the result is reversible by another Revisione.
  - One user and one window make this rare. A Trascritto generation token checked by those commands
    would close it, at the cost of a column and a command parameter. Not adopted; revisit if it is
    ever observed.
- **Release: R2.** The S2 action, the Parlanti purge and the view invalidation are wired only by
  `avvio-parlanti`. The Trascrizione-side changes (INV-4, migration, replacement, read-model) are
  release-neutral. They are built in R2's wave and are harmless in R1, where no composition offers
  the action.
- **Boundary/manifest changes** are listed in
  `features/trascrizione-con-parlanti/manifest-deltas/2026-09-24-ritrascrivi.md` (AC-425…). The
  manifest is authoritative for them once folded by `build-manifest`.
- **Amended in place with pointers here:**
  - ADR 0007: `elaborazione_completata_unica` is dropped, and its presence clause is removed from
    `enforced_by`.
  - ADR 0014: "Ritrascrivi" also offers the field.
  - `architecture.md`: the ways to start an `Elaborazione`.
- **To be folded by their owners** (texts in the delta):
  - the tactical model: [INV-4], [INV-5], [INV-12] scope, the new event, and the policy row;
  - the `context-map.md` ubiquitous language: `Elaborazione` says "v1: at most ONE completed", and
    "Ritrascrivi" and `TrascrittoSostituito` need entries;
  - `UI/ux-proposal.md`: S2 and S3.
- **Enforcement.**
  - `enforced_by` has two clauses:
    - **presence**: `3.sqm` drops the index, on one line;
    - **prohibition**: no Trascrizione source uses the generated Parlanti queries.
  - The prohibition clause was validated on 2026-09-24 against the integration line: **exit 0**.
  - The presence clause is exigible from block `persistenza-ritrascrivi`. Before that it fails by
    design, because `3.sqm` does not exist.
  - The ADR 0012 (b) prohibition covers the new policy in `..politiche`.
  - Discursive (code review): the purge never runs after commit; `TrascrittoSostituito` is published
    before `ElaborazioneCompletata` and only on replacement; `AvviaElaborazione` never writes the
    Trascritto.

## Amendment 2026-09-24 (b) — the user's answers: INV-25 confirmed, S3 read-only during a re-run, cancel a queued Elaborazione [user]
Source: the user's answers of 2026-09-24 to the open points of this ADR. The text above is kept; the
points it changes carry a pointer here. New ACs AC-461…AC-479 are in the same manifest delta
(`manifest-deltas/2026-09-24-ritrascrivi.md`, section "Amendment 2026-09-24 (b)").

### 1. Parlanti left without Attribuzione — [INV-25] confirmed
Point 3 stands as proposed. After the purge, an `attivo` `occasionale` left with no `Attribuzione` is
deleted (`rimuovi`, [INV-25]). A `ricorrente` is **always** kept (Nome, tipo, prints from other
Registrazioni), even with zero Attribuzioni and zero prints. An `eliminato` tombstone is kept. No AC
changes: AC-429 already states this.

### 2. S3 is READ-ONLY while a re-run is queued or running (replaces §4 "S3 stays fully usable")
- **When.** A Trascritto exists and the latest `Elaborazione` of the shown Registrazione is
  `in_attesa` or `in_corso` (the same pair S2 uses: `stato` + `trascrittoDisponibile` of
  `stati-elaborazione`).
- **Still allowed.** Reading the old transcript, playing from a Segmento, "▶ estratto", "Apri
  documento" / "Mostra nella cartella".
- **Disabled.** Naming ("Conferma", "altri ▾", "nuovo…", "cambia"), "salta", "Unisci con ▾" and the
  merge banner's action, "Dividi voce" and "Riassegna a ▾". No command is sent. The panel starts no
  Proposta job while read-only: no action could use it, and it would only wait on the native Mutex
  held by the running pipeline (ADR 0017).
- **Banner.** "Ritrascrizione in corso: modifiche disabilitate fino al termine". A second line says
  "Questa trascrizione sarà sostituita quando la nuova sarà pronta." In R2 the panel adds "Le
  correzioni e i nomi assegnati andranno persi."
- **End of read-only.** It ends on the `Cambiamento` that shows another state:
  - re-run `completata` → S3 reloads on the new generation (AC-453/455), editable;
  - re-run `fallita` → editing is enabled again on the unchanged old transcript (nothing was lost);
  - re-run cancelled while queued (§3 below) → the same: editing enabled again, nothing lost.
- **Enforcement level.** It is a presentation rule (S3 presenter), not a domain guard. The domain
  keeps the §6 and ADR 0012 (b) guarantees unchanged.
- **This closes the "Accepted residual race" (Consequences, point 4) for S3.** From the moment a
  re-run is queued, S3 issues no Revisione, Conferma or Salta. A stale S3 therefore cannot act on the
  new Voci: at the replacement COMMIT it was already read-only, and it reloads on the new
  generation before any action is enabled again. The only stragglers are Conferma/Salta commands
  already pending in the per-project scope (ADR 0017 §3, AC-418) from before the re-run was
  queued. They keep the `SorgenteImpronta` re-read guard and resolve as `VoceCambiata` /
  `VoceNonTrovata` (AC-455). A Revisione cannot be pending in that scope. The generation token stays
  not adopted.

### 3. NEW — cancel a queued `Elaborazione`: `AnnullaElaborazione(elaborazioneId)`
- **Scope.** An `Elaborazione` that is `in_attesa`, i.e. never started, whether a first
  transcription ("Trascrivi", "Riprova") or a re-run ("Ritrascrivi"). Actor: the utente, from S2.
- **A running (`in_corso`) Elaborazione cannot be cancelled by this command.** Neither can a
  `completata` or `fallita` one. Stopping a running pipeline is out of scope (it would need
  interruption of the native calls, ADR 0017, and a new outcome); the user waits for it to end.
- **Domain effect: the never-started row is DELETED.** No new state, so **[INV-3] is unchanged**
  (`completata` and `fallita` stay the only terminal states). An `in_attesa` row has produced
  nothing: no Trascritto, no published event, no reference from any other table. Removing it
  therefore loses no history, like the physical deletion of an unreferenced `occasionale` ([INV-25],
  tactical R25). This is the only physical deletion of an `Elaborazione`.
  - The Registrazione returns to the state derived from its **remaining** latest Elaborazione:
    `NON_AVVIATA` if none is left (a cancelled first "Trascrivi"); `COMPLETATA` with its old
    Trascritto (a cancelled "Ritrascrivi"); `FALLITA` (a cancelled "Riprova", or a cancelled re-run
    after a failed re-run). [INV-4] and [INV-5] are untouched: the Trascritto is never read or
    written by the cancellation.
  - Aggregate: `Elaborazione.annulla(): Esito<ElaborazioneAnnullata>`, Ok only from `in_attesa`;
    from any other state → `Errore(ElaborazioneGiaAvviata(elaborazioneId))`, state unchanged. It
    is a check that returns the domain event, not a transition.
  - Port: `ElaborazioneRepository.trova(id: ElaborazioneId): Elaborazione?` and
    `rimuoviInAttesa(id: ElaborazioneId): Esito<Unit>`, a **compare-and-delete**: it deletes the row
    iff it exists and is still `in_attesa`; if the row is started → `ElaborazioneGiaAvviata`; if it
    is absent → `ElaborazioneNonTrovata`. SQL: `DELETE FROM elaborazione WHERE id = :id AND stato =
    'in_attesa'`, 0 rows → re-read to tell the two errors apart.
  - New errors in `ErroreTrascrizione` (`ErroriTrascrizione.kt`): `ElaborazioneGiaAvviata(elaborazioneId)`,
    `ElaborazioneNonTrovata(elaborazioneId)`.
  - Service `AnnullaElaborazioneServizio` (`:trascrizione:applicazione ..comandi`), one transaction:
    `trova` → `annulla()` → `rimuoviInAttesa` → publish `ElaborazioneAnnullata(registrazioneId)`.
- **The race with the dispatcher.** The queue claims the oldest `in_attesa` and marks it `in_corso`
  in ONE short transaction that reads the head inside it (AC-314's pinned requirement). Since
  fix-batch-17 every transaction starts with `BEGIN IMMEDIATE`, so the claim and the cancellation
  are serialized on SQLite's write lock. Exactly one wins:
  - **the cancellation commits first** → the row is gone; the claim, reading inside its own
    transaction, never sees it and takes the next `in_attesa`, or none (Ok without effect, AC-68).
    The queue needs no signal and no change: a cancellation never makes work available;
  - **the claim commits first** → the row is `in_corso`; the cancellation fails cleanly with
    `Errore(ElaborazioneGiaAvviata)`, writes nothing, publishes nothing, and the run proceeds
    normally. The compare-and-delete makes this hold even if the service's own read was stale.

  Never both, and never a raw `SQLITE_BUSY` (fix-batch-17).
- **Events.** A new published event on boundary `eventi-elaborazione`:
  **`ElaborazioneAnnullata(registrazioneId: RegistrazioneId) : EventoPubblicato`**, published in the
  cancelling transaction and delivered **after commit only** (never on rollback). No synchronous
  subscriber. No `TrascrittoSostituito`, no `ElaborazioneFallita`. Consumers:
  - `AggiornamentiVistaTrascrizione` (R1 composition) → ONE `Cambiamento(registrazioneId)`. That
    refreshes S2 (the row, and the `posizioneInCoda` of every other queued row, since S2 reloads
    its whole list on any `Cambiamento`) and S3 if it shows that Registrazione (the read-only banner
    goes away).
  - Nothing else: Documento (no `Rigenerazione`: the Trascritto did not change), Parlanti (no purge,
    no Proposta invalidation), `CodaElaborazioni` (no signal).
- **UI (S2).** An **"Annulla"** button on a row that is "In coda (n)" or "Ritrascrizione in coda (n)",
  and on no other row (not on "In corso"). It sends exactly one `AnnullaElaborazione(elaborazioneId)`
  with no dialog, since nothing is lost. `stati-elaborazione` gains `elaborazioneId: ElaborazioneId?`
  (the latest Elaborazione's id, null for `NON_AVVIATA`). `operazioneInCorso` blocks a second
  click. On `ElaborazioneGiaAvviata` the row shows inline "La trascrizione è già partita: non si può
  più annullare" and reloads to "In corso". On success the row shows its previous state (above).
  The presenter shows "Annulla" only when the action is supplied (R1 and R2 compositions; not R0).
- **Release: R1.** First transcriptions are R1, so `AnnullaElaborazione`, the event, the view field
  and the S2 button are R1 and wired by `avvio-composizione`. The "Ritrascrizione in coda" case is
  reachable only in R2, where "Ritrascrivi" exists.

### 4. Build note: the `ErroreTrascrizione` sweep
Adding `ElaborazioneGiaAvviata` / `ElaborazioneNonTrovata` and deleting `ElaborazioneGiaCompletata`
changes a sealed hierarchy matched exhaustively (no `else`) in `:trascrizione:applicazione` (the
pipeline's `motivo` table) and in `:ui` (`MessaggiErrore`, AC-180). Neither order of per-block merges
compiles. So the change is **one sweep**, owned by the `elaborazione` rework: it edits the
hierarchy and every exhaustive `when` and reference (service pre-check, SQL mapping, fakes/contract,
`MessaggiErrore` with the texts of AC-477), with no other behaviour. Each block's own ACs then land
their behaviour on top.

### 5. Consequences of this amendment
- [INV-3] unchanged; the tactical model records `AnnullaElaborazione` and `ElaborazioneAnnullata`,
  and the one physical deletion of an `in_attesa` Elaborazione (like R25).
- The §4 "S3 stays fully usable" text and the residual race are superseded as noted inline.
- Enforcement is discursive (code review): the cancellation never touches the Trascritto; the
  delete is conditional on `in_attesa` in the SQL itself; the claim reads the head inside its
  transaction.
