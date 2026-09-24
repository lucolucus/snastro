# Manifest delta — "Elimina registrazione" (ADR 0020, user decision 2026-09-25)
This is the authoritative input for /mismagent:build-manifest. Fold it into building-blocks.yaml and
regenerate the rich block files.

Source: ADR 0020 (decisions/0020-elimina-registrazione.md). It amends in place, with pointers, ADR 0009, ADR 0018
Amendment (b) §3, dev-architecture-app.md, context-map.md, tactical-model.md and UI/ux-proposal.md.

New ACs are numbered from **AC-597**. The current max is AC-596, which includes part B of the restyle delta
(AC-590..596, reserved). This delta uses **AC-597…AC-636**. The new invariant is **[INV-28]**.

Release: **R2**. The action is offered only by `avvio-parlanti`, which registers both synchronous subscribers. The
Progetto, Trascrizione, Parlanti and Documento pieces are release-neutral.

## Summary of the decision (see the ADR for the rationale)
- **Command.** `EliminaRegistrazione(registrazioneId)` belongs to Progetto. It runs in ONE transaction:
  1. `trova`;
  2. `Registrazione.elimina()`, which returns the event;
  3. write the `eliminazione_in_sospeso` row;
  4. publish `RegistrazioneEliminata`. The synchronous subscribers run here:
     - Trascrizione vetoes if an Elaborazione is open, otherwise purges the Trascritto and every Elaborazione;
     - Parlanti reuses `ApplicaSostituzioneTrascrittoPolitica` (purge + INV-25);
  5. `RegistrazioneRepository.rimuovi`;
  6. COMMIT.
- **Refusal.** An Elaborazione `in_attesa` or `in_corso` → `ElaborazioneGiaAperta`, and nothing changes. There is no
  implicit cancellation [user default].
- **Files, after commit, idempotent:**
  - the Documento `.md` is removed through abbonato-documento's per-key queue;
  - the `audio/` copy and `cache/audio/<id>.wav` are removed by the `:avvio` after-commit subscriber, which also
    stops the player and makes the app forget S3 of that id.
- **Crash recovery.** At project open, `CompletaEliminazioniRegistrazioni` re-runs the removals for every
  `eliminazione_in_sospeso` row, then deletes the row.
- **Biometric.** `wal_checkpoint(TRUNCATE)` runs after commit on EVERY print removal.

## BOUNDARIES
- **B1 `eventi-progetto`** (owner eventi-pubblicati)
  - Add the pinned type `RegistrazioneEliminata: "data class(registrazioneId: RegistrazioneId, progettoId: ProgettoId,
    titolo: String, dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) : EventoPubblicato — published
    by EliminaRegistrazione INSIDE its transaction, BEFORE the registrazione row is removed; titolo/data/riferimento
    are the values at deletion (the only way after-commit consumers can locate the files)"`.
  - Supplier + `elimina-registrazione`.
  - Consumers + `abbonato-eliminazione-trascrizione` (SYNCHRONOUS), + `abbonato-revisione-parlanti` (SYNCHRONOUS),
    + `abbonato-documento` (after commit), + `avvio-parlanti` (after commit: `AggiornamentiVistaParlanti`,
    `PuliziaRegistrazioneEliminata`).
  - Delivery: add "EXCEPTION (ADR 0020): RegistrazioneEliminata has TWO synchronous subscribers (Trascrizione veto +
    purge, Parlanti purge) inside the publishing transaction; an Errore from either dooms the command and is returned
    unchanged by EliminaRegistrazione; its other subscribers are after commit".
  - related_adrs + 0020.
- **B2 `agg-registrazione`**
  - Add `"Registrazione.elimina": "(): RegistrazioneEliminata — pure: returns the domain event (id, progettoId,
    titolo, dataRegistrazione, riferimentoAudio); no state change, no guard (the only precondition, INV-28's 'no open
    Elaborazione', is Trascrizione's and is checked by its synchronous subscriber)"`.
  - Consumers + `elimina-registrazione`.
- **B3 `repo-progetto`**
  - `RegistrazioneRepository` + `rimuovi(id: RegistrazioneId) /* deletes the registrazione row inside the caller's
    transaction; absent id = no-op; the ONLY physical deletion of a Registrazione (ADR 0020) */`.
  - New pinned port `EliminazioniInSospeso: "interface { registra(e: EliminazioneInSospeso); elenco():
    List<EliminazioneInSospeso> /* by eliminataAlle, then id */; concludi(id: RegistrazioneId) /* absent = no-op */ }"`.
  - New pinned type `EliminazioneInSospeso: "data class(registrazioneId: RegistrazioneId, titolo: String,
    dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio)"`.
  - Consumers + `elimina-registrazione`, + `completa-eliminazioni`.
- **B4 `tec-pulizia-derivati` (NEW boundary, owner `porte-progetto`, consumers `completa-eliminazioni`,
  `avvio-parlanti`; in-process; contract_test consumer-driven)**
  - `PuliziaDerivatiRegistrazione: "interface { fun pulisci(e: EliminazioneInSospeso): Esito<Unit> } — removes every
    DERIVED file of a deleted Registrazione (cache/audio/<id>.wav, the Documento .md); idempotent; implemented in
    :avvio"`.
- **B5 `repo-trascrizione`**
  - `ElaborazioneRepository` + `rimuoviDiRegistrazione(id: RegistrazioneId) /* deletes EVERY Elaborazione of the
    Registrazione, any state; used only by the elimination policy after its veto (ADR 0020) */`.
  - `TrascrittoRepository` + `rimuovi(id: RegistrazioneId) /* deletes segmento, voce and trascritto rows of the
    Registrazione; absent = no-op */`.
  - Consumers + `eliminazione-registrazione-policy`.
- **Published Language primitives.** Only kernel VOs (`RegistrazioneId`, `ProgettoId`, `RiferimentoAudio`),
  `String` and `LocalDate` cross. No context sees another's aggregate.

## NEW BLOCKS

### persistenza-elimina-registrazione (NEW, adapter/schema, `:persistenza`, wave 17, R2; depends_on persistenza-conferma-segmento; related_adrs 0006, 0009, 0018, 0020)
This block owns `migrations/5.sqm` and the new queries. ADR 0020's `enforced_by` is `exigible_from` this block.
- **AC-597** `migrations/5.sqm` (schema 5 → 6, forward-only) contains only
  `CREATE TABLE eliminazione_in_sospeso (` on one line, with columns `registrazione_id TEXT NOT NULL PRIMARY KEY,
  titolo TEXT NOT NULL, data_registrazione TEXT NOT NULL, riferimento_audio TEXT NOT NULL, eliminata_alle INTEGER
  NOT NULL`, and no FK. `SnastroDatabase.Schema.version` = 6. The CR-13 migration test stays green:
  - an empty DB migrates, passes the integrity check and runs every query;
  - `Schema.migrate` from empty equals `Schema.create`.
- **AC-598** Migration fixture test. A DB frozen at version 5, holding one Registrazione with a completata
  Elaborazione, a Trascritto, an attribuzione and an impronta_vocale, is migrated to 6. Every row is intact,
  `user_version` = 6, and `eliminazione_in_sospeso` exists and is empty.
- **AC-599** New queries, each SQL-tested on a real SQLite DB:
  - `Registrazione.sq elimina: DELETE FROM registrazione WHERE id = :id;`
  - `EliminazioneInSospeso.sq`: `inserisci`, `elenco` (ORDER BY eliminata_alle, registrazione_id), `elimina`.
  - `Elaborazione.sq eliminaDiRegistrazione: DELETE FROM elaborazione WHERE registrazione_id = :registrazioneId;`
  - `Trascritto.sq elimina: DELETE FROM trascritto WHERE registrazione_id = :registrazioneId;`

  Test: deleting the registrazione row while one of its elaborazione rows exists fails immediately with an FK error.
  Deleting it after the elaborazione, segmento, voce and trascritto rows are gone succeeds.

### elimina-registrazione (NEW, application-service, `:progetto:applicazione (..comandi)`, wave 18, R2; consumes kernel-pl, agg-registrazione, repo-progetto, eventi-progetto; related_adrs 0003, 0012, 0020; invariants INV-28)
`EliminaRegistrazioneServizio(uow, registrazioni: RegistrazioneRepository, inSospeso: EliminazioniInSospeso,
eventi: DispatcherEventi)`, `fun esegui(c: EliminaRegistrazione(registrazioneId)): Esito<Unit>`.
- **AC-600** Happy path, all inside ONE `inTransazione` (fake UoW records exactly one), in this order (a recording
  fake checks the order):
  1. `trova`;
  2. `inSospeso.registra(EliminazioneInSospeso(id, titolo, data, riferimento))`, with the values of the stored
     Registrazione;
  3. `pubblica(RegistrazioneEliminata(id, progettoId, titolo, data, riferimento))`, exactly once;
  4. `registrazioni.rimuovi(id)`.

  Result: `Ok`, and `trova(id)` is then null.
- **AC-601** An unknown id → `Errore(ErroreProgetto.RegistrazioneNonTrovata(id))`. Nothing is registered, published
  or removed.
- **AC-602** A synchronous subscriber that answers `RegistrazioneEliminata` with
  `Errore(ErroreTrascrizione.ElaborazioneGiaAperta(id))` makes `esegui` return that same error. This is tested on
  `DispatcherEventiInMemoria` with the finte. Afterwards:
  - the Registrazione still exists;
  - no `eliminazione_in_sospeso` row exists;
  - no after-commit subscriber received anything.
- **AC-603** The service touches no file. `ArchivioAudio` is not among its collaborators (constructor test/Konsist).
  After-commit work belongs to subscribers.
- **AC-604** Two deletions of the same id: the first → Ok, the second → `RegistrazioneNonTrovata`, and nothing is
  published twice.

### completa-eliminazioni (NEW, application-service, `:progetto:applicazione (..comandi)`, wave 18, R2; consumes repo-progetto, tec-sonda-archivio, tec-pulizia-derivati; related_adrs 0010, 0012, 0020)
`CompletaEliminazioniRegistrazioniServizio(uow, inSospeso, archivio: ArchivioAudio, derivati:
PuliziaDerivatiRegistrazione)`, `fun esegui(): Esito<Unit>` (actor: sistema, at project open).
- **AC-605** For each row of `elenco()`, in order:
  1. `archivio.scarta(riferimentoAudio)`;
  2. `derivati.pulisci(e)`;
  3. if Ok, `concludi(id)` in its own short transaction.

  After the call, `elenco()` is empty (finte).
- **AC-606** `derivati.pulisci` → `Errore` for row A and Ok for row B. A stays pending and B is concluded. `esegui`
  returns Ok, so a pending row never blocks opening the project. A second `esegui` after the fault clears concludes
  A as well.
- **AC-607** Idempotent. Running `esegui` twice, or with the files already gone, gives the same result. With no
  pending row, it makes zero calls to `archivio`/`derivati`.

### eliminazione-registrazione-policy (NEW, application-service, `:trascrizione:applicazione (..politiche)`, wave 18, R2; consumes kernel-pl, agg-elaborazione, repo-trascrizione; related_adrs 0007, 0012, 0020; invariants INV-28)
`ApplicaEliminazioneRegistrazionePolitica(elaborazioni: ElaborazioneRepository, trascritti: TrascrittoRepository)`,
`fun applica(registrazioneId): Esito<Unit>`. It is structural only and never decodes or extracts (ADR 0012 (b)
enforced_by).
- **AC-608** Veto. With an `in_attesa` or an `in_corso` Elaborazione for r (in each case, also alongside a
  completata one) → `Errore(ElaborazioneGiaAperta(r))`. Nothing is removed: `rimuovi`/`rimuoviDiRegistrazione` are
  never called (counting finte).
- **AC-609** Purge. With `completata` + `fallita` Elaborazioni and a Trascritto → Ok. Afterwards:
  - `diRegistrazione(r)` is empty and `trova(r)` is null;
  - the Elaborazioni and Trascritto of another Registrazione are unchanged.
- **AC-610** A Registrazione with no Elaborazione (NON_AVVIATA) and no Trascritto → Ok, with no call that removes
  anything.
- **AC-611** `EstrattoreImprontaFinta`/`DecodificatoreAudioFinta` are never collaborators (none injected). The ADR
  0012 (b) prohibition grep stays green.

### abbonato-eliminazione-trascrizione (NEW, adapter, `:trascrizione:adattatori (..eventi)`, wave 19, R2; consumes eventi-progetto; depends_on eliminazione-registrazione-policy; related_adrs 0012, 0020)
- **AC-612** `AbbonatoEliminazioneRegistrazione(dispatcher, politica)` registers ONE synchronous subscriber in
  `init`:
  - `RegistrazioneEliminata(r, …)` → `politica.applica(r)` inside the publishing transaction, and its `Errore`
    dooms the transaction (test on `DispatcherEventiInMemoria` with a fake UoW);
  - every other event → Ok, with no call.

  The adapter never uses Parlanti queries (ADR 0018 prohibition).

## REWORKED BLOCKS

### registrazione (aggregate, `:progetto:dominio`)
- **AC-613** `Registrazione.elimina()` returns `RegistrazioneEliminata(id, progettoId, titolo, dataRegistrazione,
  riferimentoAudio)`. It carries the CURRENT titolo and date (after `rinomina`/`modificaData`), and the aggregate's
  state is unchanged. INV-1/INV-2 tests stay green.
- notes: "AMENDED 2026-09-25 (ADR 0020): elimina is a pure check returning the event; the physical deletion is the
  repository's rimuovi — the only deletion of a Registrazione."

### porte-progetto (port + testFixtures)
- **AC-614** `RegistrazioneRepositoryContratto` gains these cases, run against the finta and later against SQL:
  - `rimuovi(id)` → `trova(id)` is null, and `delProgetto`/`titoliDelProgetto` no longer list it;
  - `rimuovi` of an unknown id is a no-op;
  - the other Registrazioni are unchanged.
- **AC-615** New `EliminazioniInSospesoContratto` + `EliminazioniInSospesoFinta`:
  - `registra` then `elenco` contains the row with the same four values;
  - `concludi(id)` removes it;
  - `concludi` of an unknown id is a no-op;
  - `elenco` is ordered by registration time, then id.

  New `PuliziaDerivatiRegistrazioneFinta` (records calls, can be told to fail once).

### repository-sql-progetto (adapter)
- **AC-616** `RegistrazioneRepositorySql.rimuovi` over `Registrazione.sq elimina`, and a new
  `EliminazioniInSospesoSql` over `EliminazioneInSospeso.sq`. Both contracts (AC-614/615) pass against SQL.
  `data_registrazione` round-trips as ISO date and `eliminata_alle` as epoch millis.
- **AC-617** The adapter uses only `registrazioneQueries`/`eliminazioneInSospesoQueries` (plus
  `progettoQueries`). The ADR 0020 prohibition clause stays green.

### eventi-pubblicati (owner of eventi-progetto)
- **AC-618** `snastro.progetto.applicazione.eventi.RegistrazioneEliminata(registrazioneId, progettoId, titolo,
  dataRegistrazione, riferimentoAudio) : EventoPubblicato` exists with exactly these fields.
  `DispatcherEventiInMemoria` delivers it to synchronous subscribers inside the transaction, and to after-commit
  subscribers only after COMMIT, never on rollback. AC-14's event count +1.

### porte-trascrizione (port + testFixtures)
- **AC-619** `ElaborazioneRepositoryContratto`: `rimuoviDiRegistrazione(r)` removes every Elaborazione of r (any
  state) and none of another Registrazione. `TrascrittoRepositoryContratto`: `rimuovi(r)` → `trova(r)` is null,
  `conTrascritto()` no longer lists r, the other Trascritti are unchanged, and it is a no-op when absent. The finte
  honour exactly this.

### repository-sql-trascrizione (adapter)
- **AC-620** `rimuoviDiRegistrazione` over `eliminaDiRegistrazione`, and `TrascrittoRepositorySql.rimuovi` =
  `segmento`, `voce` and `trascritto` deletes (in this order) in the caller's transaction. Both contracts (AC-619)
  pass against SQL. Round-trip: after `rimuovi` the `segmento`, `voce` and `trascritto` row counts for r are 0.

### abbonato-revisione-parlanti (adapter, synchronous subscriber)
- consumes + `eventi-progetto`.
- **AC-621** `RegistrazioneEliminata(r, …)` → `ApplicaSostituzioneTrascrittoPolitica.applica(r)` inside the
  publishing transaction, and its `Errore` dooms it. The existing mappings are unchanged. The policy's KDoc names
  both triggers. The policy code and its AC-428..431 are unchanged: it is reused, not reworked.

### repository-sql-parlanti (adapter)
- **AC-622** `PRAGMA wal_checkpoint(TRUNCATE)` is registered `afterCommit` whenever `ParlanteRepositorySql.salva`
  deleted at least one `impronta_vocale` row, or `rimuovi` removed a Parlante. It never runs inside a transaction
  and never runs on rollback. It is tested with a counting query hook on:
  - an attivo Parlante losing one print → 1 checkpoint after commit;
  - the same in a rolled-back transaction → 0;
  - a salva with no print removed → 0;
  - EliminaParlante → 1 (unchanged).

### rigenerazione-documento (read-model/policy, `:documento:applicazione`)
- **AC-623** `perRegistrazioneEliminata(registrazioneId, dataRegistrazione, titolo, nomiPrecedenti: Set<String>)`
  calls `ScrittoreDocumento.rimuovi(Documento.nomeFile(data, titolo))` and `rimuovi` of each name in
  `nomiPrecedenti` that differs (case-insensitively). It never writes, and never reads the Trascritto. An
  `IOException` → `Errore(ScritturaFallita)`, so the caller retries. Removing an absent file is Ok (idempotent).

### abbonato-documento (adapter, after commit)
- consumes + `eventi-progetto` (RegistrazioneEliminata).
- **AC-624** `RegistrazioneEliminata` after commit is queued on the SAME per-registrazioneId key as Rigenerazione:
  - It REPLACES any pending regeneration of that key. The pending `dataPrecedente`/`titoloPrecedente` become the
    `nomiPrecedenti` to remove as well.
  - Ordering, tested with a fake policy: a Rigenerazione of r still running when the event arrives completes FIRST,
    and the removal runs after it.
  - Failures are retried with the existing backoff.
  - Never on rollback.

### schermata-registrazioni (ui, S2)
- AzioniRegistrazioni: + `elimina: (RegistrazioneId) -> Unit` (opens the dialog), + `confermaElimina`,
  + `annullaElimina`, + `chiudiAvviso`. The presenter takes an optional `eliminaRegistrazione:
  ((EliminaRegistrazione) -> Esito<Unit>)?` source, like AC-342: null in R0/R1.
- `RigaRegistrazione`: + `eliminazione: StatoEliminazione` (`Disponibile | NonDisponibile(motivo: String) |
  Assente`), + `confermaElimina: Boolean`.
- **AC-625** With the source supplied, EVERY row has the More menu with "Elimina…":
  - enabled on NON_AVVIATA, FALLITA and COMPLETATA rows, and on rows with a Trascritto whose latest run is FALLITA;
  - disabled on IN_ATTESA rows (first transcription or re-run), with caption "Annulla prima la trascrizione in
    coda.";
  - disabled on IN_CORSO rows, with caption "Non puoi eliminarla durante la trascrizione.".

  A disabled item sends nothing. Without the source (R0/R1), no row shows "Elimina…", and the More menu keeps its
  AC-575 content.
- **AC-626** "Elimina…" opens the dialog of the row. Title: "Eliminare «<titolo>»?".
  - With `trascrittoDisponibile`, the body is "Verranno cancellati il file audio copiato nel progetto, la
    trascrizione con le correzioni delle voci, i nomi dati alle voci, il documento e le impronte vocali ricavate da
    questa registrazione. Non si può annullare." followed by "Le persone ricorrenti restano, con le impronte delle
    altre registrazioni. Le persone occasionali che compaiono solo qui spariscono. Il file originale fuori dal
    progetto non viene toccato."
  - Without it, the body is "Verrà cancellato il file audio copiato nel progetto. Il file originale fuori dal
    progetto non viene toccato. Non si può annullare."

  Buttons "Annulla" / "Elimina" (danger primary). "Annulla" → no command. "Elimina" → exactly ONE
  `EliminaRegistrazione(id)`, and `operazioneInCorso` blocks a second click.
- **AC-627** On Ok:
  - if `LettoreAudio.stato.registrazioneId == id` and it is playing, the presenter calls `pausa()`;
  - the list reloads without the row;
  - an info notice "«<titolo>» eliminata." shows above the list until `chiudiAvviso` or the next command.
- **AC-628** Errors, shown inline on the row with the dialog closed:
  - `ElaborazioneGiaAperta` (the race backstop) → "La trascrizione è partita: non puoi eliminarla finché non è
    finita.", and the row reloads (now In coda/In corso);
  - `RegistrazioneNonTrovata` → the list just reloads.

  Any other Errore → the `MessaggiErrore` text (AC-180).
- **AC-629** Render-check at the minimum window width and at 1280×800, light and dark: the More menu open with
  "Elimina…" enabled, the menu on an "In corso" row with the disabled item and its caption, both dialog variants,
  and the notice. No clipped text. The menu and dialog use `anteprime/Menu.html` / `Dialog.html` (Pericolo primary).

### avvio-parlanti (R2 composition)
- consumes + `eventi-progetto`, + `tec-pulizia-derivati`; depends_on + `elimina-registrazione`,
  `completa-eliminazioni`, `abbonato-eliminazione-trascrizione`.
- **AC-630** The R2 composition registers `AbbonatoEliminazioneRegistrazione` (Trascrizione) and
  `AbbonatoRevisioneParlanti` BEFORE the first command, and supplies `eliminaRegistrazione` to the S2 presenter.
  Test: both synchronous subscribers are registered before `CodaElaborazioni` starts. The R1 composition
  (`avvio-composizione`) supplies no `eliminaRegistrazione` (AC-625, last clause).
- **AC-631** `AggiornamentiVistaParlanti`: `RegistrazioneEliminata` after commit → `ProposteSerializzate.invalidaTutte()`
  + ONE `Cambiamento(null)`. It is never delivered on rollback.
- **AC-632** `PuliziaRegistrazioneEliminata` (after commit), in this order:
  1. if the `LettoreAudio` holds that `registrazioneId`, it pauses it;
  2. `ArchivioAudio.scarta(riferimentoAudio)`;
  3. deletes `cache/audio/<id>.wav` (idempotent);
  4. `NavigazioneProgetto.dimentica(id)`: a current place `Registrazione(id)`, or a `primaDiModelli` of
     `Registrazione(id)`, becomes `Registrazioni`, and other places are untouched.

  An I/O failure is logged and does not throw. The pending row stays for AC-633.
- **AC-633** Startup sequence: after `RigeneraTuttiIDocumenti` is queued, `CompletaEliminazioniRegistrazioni.esegui()`
  runs with `PuliziaDerivatiRegistrazione` implemented here: delete `cache/audio/<id>.wav`, then
  `RigenerazioneDocumentoPolitica.perRegistrazioneEliminata(id, data, titolo, ∅)`. Test on a project folder where a
  pending row exists and `audio/<id>.m4a`, `cache/audio/<id>.wav` and `documenti/<nomeFile>` are all still present
  (simulated crash after commit). After opening, the three files are gone and `eliminazione_in_sospeso` is empty.
  An unrelated user file `documenti/appunti.md` is untouched.
- **AC-634** E2E on a real SQLite file with the fake ML ports.
  - Setup: Registrazione R completata, with:
    - Voce 1 → `ricorrente` "Mario" (print), also attributed with a print in Registrazione Q;
    - Voce 2 → `occasionale` "Ospite del 12/09/2026" (print) seen only in R;
    - Voce 3 → an `eliminato` tombstone's Attribuzione;
    - a Documento file for R, and the audio/ and cache/audio/ files for R.
  - Action: `EliminaRegistrazione(R)`, then wait for the after-commit work.
  - Expected:
    - zero rows keyed by R in `registrazione`, `elaborazione`, `trascritto`, `voce`, `segmento`, `attribuzione` and
      `impronta_vocale`;
    - Mario exists with his Q Attribuzione and print (`numImpronte` 1, `numRegistrazioni` 1 in
      `parlanti-del-progetto`);
    - the Ospite no longer exists;
    - the tombstone still exists (still eliminato, same Nome);
    - `audio/R.*`, `cache/audio/R.wav` and R's `.md` are gone, while Q's files are untouched;
    - S2 lists only Q;
    - exactly one `wal_checkpoint(TRUNCATE)` ran after the commit.
  - The `eliminazione_in_sospeso` row for R still exists until the next open, and after reopening it is empty.
- **AC-635** E2E refusals, with the same setup plus a second Registrazione S:
  - S `in_corso` (held pipeline) → `EliminaRegistrazione(S)` returns `ElaborazioneGiaAperta`;
  - S re-queued `in_attesa` → the same.

  In both cases every row and file of S is unchanged, no `eliminazione_in_sospeso` row exists, and no after-commit
  event is delivered. After `AnnullaElaborazione` of the queued run, `EliminaRegistrazione(S)` → Ok.
- **AC-636** Race, on a real SQLite FILE database with two `UnitaDiLavoroSql` threads started on a barrier, repeated
  100 times: `EliminaRegistrazione(R)` against `AvviaElaborazione(R)`. Every run ends in exactly one of:
  - R deleted, and the start got `RegistrazioneNonTrovata`, with no elaborazione row;
  - R intact with one `in_attesa`, and the deletion got `ElaborazioneGiaAperta`.

  Never both, and never a thrown exception or an FK failure.

## Not changed
- **sostituzione-trascritto-policy.** It is reused as it is. AC-428..431 already prove the purge and INV-25 for
  "every VoceRef of r".
- **riallinea-impronte, conferma-attribuzione, salta-voce.** Their in-transaction re-read and compare-and-set
  already refuse or ignore a deleted Voce (ADR 0012 (b)). No new AC.
- **annulla-elaborazione, avvia-elaborazione, esegui-elaborazione.** AvviaElaborazione's in-transaction
  `RegistrazioneNonTrovata` (AC-67) covers a deleted Registrazione, and AC-636 proves it under a race.
- **ui-fondamenta MessaggiErrore.** No new variant: the veto reuses `ElaborazioneGiaAperta`. The S2 presenter maps
  it contextually for this action (AC-628).
- **schermata-registrazione (S3).** No change: navigation forgets the deleted id (AC-632).
- **schermata-parlanti (S4).** No change: it reloads on `Cambiamento(null)` and its view is computed on read.

## Build order (waves)
- wave 17: `persistenza-elimina-registrazione`.
- wave 18, all in parallel after the reworks of the ports they consume:
  - `registrazione`, `porte-progetto`, `porte-trascrizione` and `eventi-pubblicati` (reworks) first;
  - then `elimina-registrazione`, `completa-eliminazioni`, `eliminazione-registrazione-policy`;
  - `rigenerazione-documento` (rework).
- wave 19, in parallel:
  - `repository-sql-progetto`, `repository-sql-trascrizione`, `repository-sql-parlanti` (reworks, after wave 17);
  - `abbonato-eliminazione-trascrizione` (new);
  - `abbonato-revisione-parlanti`, `abbonato-documento` (reworks).
- wave 20: `schermata-registrazioni` (rework; after restyle-shell-elenchi, since the More menu is the restyled one).
- wave 21: `avvio-parlanti` (rework: wiring + e2e AC-630..636).

Gate unchanged (`./gradlew check`). ADR 0020's presence clause turns exigible when wave 17 merges.

## Texts to fold by their owners (already amended in place by the architect on 2026-09-25)
- `tactical-model.md` — § Amendment 2026-09-25 (ADR 0020): the command, the event, [INV-28], the policies and the
  R25-like deletion.
- `context-map.md` — § Progetto, `Eliminare una Registrazione` + "Not:" list.
- `UI/ux-proposal.md` — § Amendment 2026-09-25 (Elimina registrazione).
- `architetture/dev-architecture-app.md` — "no deletion method" exceptions list.
- ADR 0009 / ADR 0018 — pointer amendments.
