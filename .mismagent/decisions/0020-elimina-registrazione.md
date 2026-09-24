---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0009 (a new print-removal path + WAL checkpoint on every removal), ADR 0018 Amendment (b) §3 ("the only physical deletion of an Elaborazione"), dev-architecture-app.md ("no deletion method"), tactical R25 note
closes_spike: null
enforced_by:
  kind: presence+prohibition
  rule: "grep -qE '^CREATE TABLE eliminazione_in_sospeso[[:space:]]*[(]' persistenza/src/main/sqldelight/migrations/5.sqm && ! grep -rnE --include='*.kt' --exclude-dir=build '(elaborazioneQueries|trascrittoQueries|voceQueries|segmentoQueries|attribuzioneQueries|improntaVocaleQueries|parlanteQueries)' progetto | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
  exigible_from: "persistenza-elimina-registrazione"   # validated 2026-09-25 via bash -c: tree exit 2 (5.sqm absent — red BY DESIGN until that block); prohibition clause alone exit 0 on the tree; fixtures: PASS (table on one line + only registrazioneQueries and commented mentions in progetto/), FAIL on `db.elaborazioneQueries…` in progetto/, FAIL when the CREATE line is commented out
---
# 0020 — "Elimina registrazione": a hard delete across the four contexts. One transaction for the rows, file removal after commit, and a pending-cleanup row for crash recovery

## Context
**The user decided this on 2026-09-25 [user]** and accepted all the defaults proposed to them. They want to delete a
`Registrazione` from a `Progetto`. Today no deletion exists anywhere:
- the aggregates have no deletion method (dev-architecture-app.md: "soft state only");
- the exceptions so far are the biometric rows (ADR 0009), an `occasionale` that ceases to exist ([INV-25], R25) and a
  never-started `Elaborazione` (ADR 0018 Amendment (b) §3).

What the user asked for:
1. The S2 row's More menu gets "Elimina…", with a confirmation dialog that lists what is lost.
2. It is a **hard** delete of:
   - the audio copy in the project folder;
   - the `Trascritto` (`Segmento`s, `Voce`s) and its `Elaborazione`s;
   - the `Documento` `.md`;
   - every `ImprontaVocale` sourced from this `Registrazione`. These are biometric data (ADR 0009) and must really be
     deleted, as with `EliminaParlante`.
3. `ricorrente` `Parlante`s stay, with their prints from other recordings. The `Attribuzione`s keyed by this
   recording's `VoceRef`s are removed. An `occasionale` left with no `Attribuzione` ceases to exist ([INV-25], the
   same mechanism as the `TrascrittoSostituito` purge of ADR 0018 §3).
4. It is refused while an `Elaborazione` is `in_corso`, with a clear message. If one is `in_attesa`, the user
   cancels it first (`AnnullaElaborazione`). **User default: cancel first. The delete does not cancel it
   implicitly.**
5. The `Registrazione` disappears from S2 and from the Parlanti views (S4 counts, `Proposta`s, `Galleria`). If S3 of
   that recording is open, the app goes back to the list.

**Facts checked in the code (2026-09-25, branch `feature/trascrizione-con-parlanti`):**
- The source **is** copied: `ArchivioAudioFile.copia` writes `audio/<registrazioneId>.<ext>` (ADR 0010, AC-59), and
  `ArchivioAudio.scarta(r)` already exists. It is idempotent and confined to `audio/`.
- A derived WAV `cache/audio/<registrazioneId>.wav` is written by both `DecodificatoreAudioFfmpeg`s (Trascrizione
  and Parlanti adapters). If it is missing, they rebuild it from `audio/`.
- The `.md` file is `documenti/<Documento.nomeFile(dataRegistrazione, titolo)>`. `ScrittoreDocumento.rimuovi(nomeFile)`
  exists and is idempotent. The name depends on `titolo` and `dataRegistrazione`, and both can change
  (`RinominaRegistrazione`, `ModificaDataRegistrazione`).
- **Schema FKs** (1.sqm):
  - `trascritto`, `elaborazione` → `registrazione(id)` are **immediate**;
  - `voce` → `trascritto` is immediate;
  - `segmento`, `attribuzione`, `impronta_vocale` → `voce` are **DEFERRABLE INITIALLY DEFERRED**.

  So the `registrazione` row can be deleted only after its `elaborazione` and `trascritto` rows are gone. A
  forgotten Parlanti purge fails the COMMIT, so the design fails closed.
- `DispatcherEventiInMemoria`: a synchronous subscriber's nested `Esito.Errore` dooms the whole command, and the
  outer `inTransazione` **returns that error** (kernel test "un comando eseguito da un abbonato sincrono … il suo
  Errore la annulla"). Every transaction starts with `BEGIN IMMEDIATE` (fix-batch-17).
- `ApplicaSostituzioneTrascrittoPolitica.applica(registrazioneId)` (`:parlanti:applicazione ..politiche`) already
  does exactly point 3: it purges every `Attribuzione` and print of the `Registrazione`, for every `Parlante`, and
  then applies [INV-25].
- `ParlanteRepositorySql` runs `PRAGMA wal_checkpoint(TRUNCATE)` after commit **only** when it saves an `eliminato`
  `Parlante`. The purges of [INV-15], [INV-21], [INV-25] and ADR 0018 do not run it.
- The latest migration is `4.sqm` (schema 4 → 5), so `Schema.version` is 5. The next migration is `5.sqm` (5 → 6).

## Decision

### 1. The command and the event (canonical names)
- **`EliminaRegistrazione(registrazioneId)`**. Actor: the utente, through S2 → "Elimina…" → confirm. It lives in
  **Progetto** (`:progetto:applicazione ..comandi`, `EliminaRegistrazioneServizio`), because Progetto owns the
  `Registrazione` and the catalogue.
- **`RegistrazioneEliminata`**. This is a domain event of `Registrazione`, published on boundary `eventi-progetto`
  (Published Language, package `snastro.progetto.applicazione.eventi`):
  `data class RegistrazioneEliminata(registrazioneId: RegistrazioneId, progettoId: ProgettoId, titolo: String,
  dataRegistrazione: LocalDate, riferimentoAudio: RiferimentoAudio) : EventoPubblicato`.
  - After commit the `Registrazione` no longer exists, so the event carries what the after-commit consumers need to
    find its files: the `titolo` and date for the `Documento` name, and the `riferimentoAudio`.
  - It carries kernel VOs and primitives only.
- In the domain, **`Registrazione.elimina(): RegistrazioneEliminata`** is a pure check that returns the event. It is
  not a state transition (the same pattern as `Elaborazione.annulla()`). The physical removal is
  `RegistrazioneRepository.rimuovi(id)`. It is the **second** physical deletion of a root in Progetto's model,
  alongside R25, and it is allowed for the same reason: after this command nothing references the row.

### 2. Who deletes what (bounded-context ownership). The rows go in ONE SQLite transaction
`EliminaRegistrazioneServizio.esegui` runs **one** `UnitaDiLavoro` transaction (`BEGIN IMMEDIATE`), in this order:
1. `registrazioni.trova(id)`. If it is absent → `Errore(ErroreProgetto.RegistrazioneNonTrovata(id))`, and nothing is
   written.
2. `evento = registrazione.elimina()`.
3. `inSospeso.registra(EliminazioneInSospeso(id, titolo, dataRegistrazione, riferimentoAudio))`. This writes the
   pending-cleanup row (§4).
4. `eventi.pubblica(evento)`. The **synchronous** subscribers (ADR 0012) run inside the transaction:
   - **Trascrizione** — `AbbonatoEliminazioneRegistrazione` (`:trascrizione:adattatori ..eventi`) →
     **`ApplicaEliminazioneRegistrazionePolitica.applica(registrazioneId)`** (`:trascrizione:applicazione
     ..politiche`, structural). It re-reads `elaborazioni.diRegistrazione(id)` **inside** the transaction:
     - if any is `in_attesa | in_corso` → `Errore(ErroreTrascrizione.ElaborazioneGiaAperta(id))`. This is an
       existing variant, so there is no sealed-hierarchy sweep. The error dooms the command and is returned
       unchanged by `EliminaRegistrazione`.
     - Otherwise it calls `trascritti.rimuovi(id)` (the `segmento`, `voce` and `trascritto` rows) and
       `elaborazioni.rimuoviDiRegistrazione(id)` (**every** `Elaborazione`, `completata`/`fallita` history included).
     - With no `Trascritto` and no `Elaborazione` it returns Ok and does nothing.
   - **Parlanti** — the existing `AbbonatoRevisioneParlanti` gains `RegistrazioneEliminata →
     ApplicaSostituzioneTrascrittoPolitica.applica(registrazioneId)`, **reused unchanged**:
     - every `Attribuzione` and print keyed by a `VoceRef` of the `Registrazione` goes, for every `Parlante`;
     - then [INV-25] applies: an `attivo` `occasionale` left without an `Attribuzione` is removed with `rimuovi`, a
       `ricorrente` is kept with its other prints, and an `eliminato` tombstone is kept.

     The policy's name says "sostituzione"; it is kept to avoid churn, and the KDoc gains the second trigger.
5. `registrazioni.rimuovi(id)` removes the `registrazione` row. This must come **after** step 4, because the
   `elaborazione`/`trascritto` FKs are immediate.
6. COMMIT. The deferred FKs are checked here: a `segmento`/`attribuzione`/`impronta_vocale` row still pointing at a
   removed `voce` fails the COMMIT, and the whole command rolls back.

**Race safety.** `BEGIN IMMEDIATE` serializes the deletion against `AvviaElaborazione`, the queue's claim,
`AnnullaElaborazione`, `ConfermaAttribuzione`/`SaltaVoce` and every Revisione. Their existing in-transaction re-reads
decide the outcome:
- a start that commits first → the Trascrizione veto;
- a deletion that commits first → `AvviaElaborazione` answers `RegistrazioneNonTrovata` (AC-67, checked in its
  transaction);
- a pending Conferma/Salta → `VoceNonTrovata`/`TrascrittoNonTrovato`, and nothing is written (ADR 0012 (b));
- `RiallineaImpronte` → a compare-and-set that finds no row. It never INSERTs, so nothing is resurrected.

**Point 4 of the user's list: cancel first [user default].** `EliminaRegistrazione` does **not** cancel a queued
`Elaborazione`. It is refused with the same `ElaborazioneGiaAperta` in both cases (`in_attesa`, `in_corso`). S2 does
not offer the action in those states and gives the reason (§6).

### 3. Files are deleted AFTER COMMIT, idempotently, each by its owner
No file is touched inside the transaction (ADR 0012). If the command rolls back, every file is intact.
- **`Documento` `.md` (Documento owns it).** `abbonato-documento` (after commit) queues a removal on the **same
  per-`registrazioneId` queue** as `Rigenerazione`. That queue is drained by its single coroutine, so a
  `Rigenerazione` that read the `Trascritto` before the COMMIT always writes **before** the removal runs. The
  removal cannot be overtaken. The new policy method is `RigenerazioneDocumentoPolitica.perRegistrazioneEliminata(
  registrazioneId, dataRegistrazione, titolo, nomiPrecedenti)`:
  - it removes `Documento.nomeFile(data, titolo)`;
  - it also removes the names still pending from an unflushed rename or date change in the coalesced entry (its
    `dataPrecedente`/`titoloPrecedente`);
  - the removal **replaces** any regeneration pending for that key;
  - it is retried with the existing backoff.

  Any later `Rigenerazione` of that id finds no `Trascritto` and writes nothing (AC-153).
- **Audio copy + derived WAV (Progetto + technical).** An after-commit subscriber in `:avvio` (R2 composition),
  `PuliziaRegistrazioneEliminata`, does three things:
  - it stops the `LettoreAudio` if that `Registrazione` is loaded;
  - it calls `ArchivioAudio.scarta(riferimentoAudio)`;
  - it deletes `cache/audio/<id>.wav`. `:avvio` already owns the project-folder layout (architecture.md R3).

  Both deletions are idempotent. A failure (e.g. a file held open on Windows) is logged and left to the recovery
  rule in §4.
- **The biometric rows' WAL pages.** The `impronta_vocale` rows are deleted in the transaction, and
  `secure_delete=ON` zeroes the freed pages (ADR 0009). **`ParlanteRepositorySql` now registers
  `PRAGMA wal_checkpoint(TRUNCATE)` after commit whenever `salva` or `rimuovi` removed at least one `impronta_vocale`
  row**, not only for an `eliminato` `Parlante`. This closes the gap for every removal path: [INV-15] moves,
  [INV-21], [INV-25], ADR 0018 and this ADR.

### 4. Crash recovery: the `eliminazione_in_sospeso` row
A crash between the COMMIT and the file removals would leave orphan files. For `documenti/` it would also lose the
information needed to name them. So:
- A new Progetto-owned table is created by migration **`5.sqm`** (schema 5 → 6, forward-only, ADR 0006 (a)):
  `CREATE TABLE eliminazione_in_sospeso (registrazione_id TEXT NOT NULL PRIMARY KEY, titolo TEXT NOT NULL,
  data_registrazione TEXT NOT NULL, riferimento_audio TEXT NOT NULL, eliminata_alle INTEGER NOT NULL)`. It has no
  FK, because the `registrazione` row is gone. The row is written **in the deletion transaction** (§2 step 3), so it
  exists iff the deletion committed.
- **At project open** a new Progetto service, **`CompletaEliminazioniRegistrazioni`** (actor: sistema), runs for
  every pending row, in the startup sequence after `RigeneraTuttiIDocumenti` is queued:
  1. `ArchivioAudio.scarta(riferimentoAudio)`;
  2. `PuliziaDerivatiRegistrazione.pulisci(e)`. This is a new Progetto-owned port, implemented in `:avvio`. It
     deletes `cache/audio/<id>.wav` and calls `RigenerazioneDocumentoPolitica.perRegistrazioneEliminata(id, data,
     titolo, ∅)`;
  3. only if step 2 is Ok → `inSospeso.concludi(id)` deletes the row in a short transaction. An error leaves the row
     for the next open.

  All the steps are idempotent, so re-running them after a crash is safe.
- **The row is closed at the next project open, not right after the after-commit removals.** Closing it earlier
  would need a cross-owner completion signal, and it would re-open the in-flight-`Rigenerazione` race of §3. At
  startup nothing for that id can be in flight: it has no `Trascritto`, so `RigeneraTuttiIDocumenti` skips it. The
  row holds **no biometric data**, only the title, date and audio path. They stay in `progetto.db` until the next
  open, and `secure_delete` zeroes them on removal.

### 5. Views and navigation
- **S2.** The row disappears. The after-commit `AggiornamentiVistaParlanti` maps `RegistrazioneEliminata` to
  `ProposteSerializzate.invalidaTutte()` + **one `Cambiamento(null)`**. The cached `Proposta`s may point at the
  deleted `Voce`s or use their prints, and S4 counts may have changed.
- **S4, `Proposta`, `Galleria`.** All are computed on read (R15). After the purge they no longer see the recording:
  `numRegistrazioni`, `numImpronte` and `ultimaApparizione` drop, and an `occasionale` seen only there is gone.
- **S3 of that recording.** `:avvio`'s `NavigazioneProgetto` gains `dimentica(id)`, called by
  `PuliziaRegistrazioneEliminata`: if the current place, or the place remembered for leaving S5 (`primaDiModelli`),
  is `Registrazione(id)`, it becomes `Registrazioni`. The app can therefore never land on, or return to, S3 of a
  deleted recording.

### 6. UI texts (Italian; tone per `UI/design-system/README.md` and `anteprime/Dialog.md`)
- **More menu.** It is present on **every** S2 row in R2. It holds "Elimina…" (Trash icon), plus "Ritrascrivi" where
  it is offered today (AC-575). The item is **disabled** with a caption:
  - on an "In coda" row: "Annulla prima la trascrizione in coda.";
  - on an "In corso" row: "Non puoi eliminarla durante la trascrizione.".
- **Dialog**, when a `Trascritto` exists:
  - title: "Eliminare «<titolo>»?";
  - text: "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, i
    nomi dati alle voci, il documento e le impronte vocali ricavate da questa registrazione. Non si può annullare.";
  - second paragraph: "Le persone ricorrenti restano, con le impronte delle altre registrazioni. Le persone
    occasionali che compaiono solo qui spariscono. Il file originale fuori dal progetto non viene toccato.";
  - buttons: "Annulla" / **"Elimina"** (danger primary).
- **Dialog**, when there is no `Trascritto` (the row is "Da trascrivere", or failed with no transcript):
  - the same title;
  - text: "Verrà cancellato il file audio copiato nel progetto. Il file originale fuori dal progetto non viene
    toccato. Non si può annullare.";
  - the same buttons.
- **Race backstop.** `ElaborazioneGiaAperta` returned by this action shows, inline on the row: "La trascrizione è
  partita: non puoi eliminarla finché non è finita." The generic `MessaggiErrore` text is unchanged.
- **Success.** An info notice above the list, which the user can close: "«<titolo>» eliminata."

### 7. Invariant — [INV-28] (new; next free number, after INV-27)
> [INV-28] A `Registrazione` can be eliminata only if none of its `Elaborazione`s is `in_attesa | in_corso`. Its
> elimination removes, **in one transaction**:
> - the `Registrazione`;
> - every `Elaborazione` of it;
> - its `Trascritto` (`Voce`s, `Segmento`s);
> - every `Attribuzione` and `ImprontaVocale` keyed by one of its `VoceRef`s, followed by [INV-25].
>
> After the COMMIT no row keyed by its `registrazioneId` survives except its `eliminazione_in_sospeso` row. That row
> is removed once its files (`audio/`, `cache/audio/`, the `Documento`) are gone. A refused or failed elimination
> changes nothing.

It is tested by `elimina-registrazione` (service, fakes), `eliminazione-registrazione-policy` (veto and purge) and
the `avvio-parlanti` e2e (real SQLite, row counts per table).

## Privacy (ADR 0009, extended)
- This is a **new print-removal path**. The prints are deleted in the command transaction through the same deletion
  as every other path (`Parlante.rimuoviImpronta` + `salva`, `secure_delete=ON`), followed by the after-commit
  `wal_checkpoint(TRUNCATE)` of §3, which now covers **every** path.
- This ADR creates no other copy of a print. `eliminazione_in_sospeso` holds no biometric data.
- The ADR 0009 caveat still holds, and the dialog makes no promise about it: copies of the project folder made
  outside the app (Time Machine, sync services) may still hold the audio, the `.md` and the prints.

## Rejected options
- **Soft delete (a `stato = eliminata` on `Registrazione`).** The user asked for a hard delete. Keeping the audio and
  the prints would contradict the privacy intent of ADR 0009.
- **Implicitly cancelling an `in_attesa` `Elaborazione`.** Rejected by the user default (point 4). The deletion is
  destructive, and the user states each step explicitly.
- **Deleting files inside the transaction, or before it.** A rollback would then lose files the DB still
  references. ADR 0012 forbids file work in a transaction.
- **The Trascrizione/Parlanti purge by `ON DELETE CASCADE`.** `TrascrittoRepository.salva` deletes and re-inserts
  `voce` on every Revisione, so a cascade from `voce` would wipe `Attribuzione`s (ADR 0018, rejected options). A
  cascade from `registrazione` would put the veto and [INV-25] into SQL, where RC-1 does not allow them.
- **Orchestrating the deletion in `:avvio` glue** (check state, then call three contexts). The veto would then be a
  pre-check outside the writing transaction, which is racy against the queue claim. The glue would also hold a
  domain rule (architecture.md: "the glue holds no domain rule").
- **Recovery by sweeping directories for orphans.** This is safe for the app-owned `audio/` and `cache/audio/`, but
  not for `documenti/`: the user may keep their own `.md` files there, and ADR 0010 forbids reading a `.md` back to
  recognise it. The pending row is exact and needs no guessing.
- **Closing the pending row right after the after-commit removals.** This needs a completion signal across three
  owners, and it races with an in-flight `Rigenerazione` (§4).

## Consequences
- **Release: R2.** The action is offered only by the composition that registers **both** synchronous subscribers
  (`avvio-parlanti`). R0 and R1 compositions show no "Elimina…".
  - Without the Parlanti subscriber the deferred FKs would fail the COMMIT, which is safe but useless. Without the
    Trascrizione subscriber the immediate FK on `elaborazione`/`trascritto` would fail the delete.
  - Progetto, Trascrizione, Parlanti and Documento pieces are release-neutral.
- **Schema.** `5.sqm` (5 → 6). New queries:
  - `Registrazione.sq elimina`;
  - `EliminazioneInSospeso.sq inserisci`, `elenco`, `elimina`;
  - `Elaborazione.sq eliminaDiRegistrazione`. This amends ADR 0018 (b)'s "eliminaInAttesa is the only DELETE on
    `elaborazione`";
  - `Trascritto.sq elimina`.
- **Amended in place with pointers here:**
  - ADR 0009 (removal paths + checkpoint);
  - ADR 0018 Amendment (b) §3 (the only deletion of an `Elaborazione`);
  - `architetture/dev-architecture-app.md` (the "no deletion method" rule lists this exception);
  - `context-map.md` (ubiquitous language);
  - `tactical-model.md`;
  - `UI/ux-proposal.md` (S2).
- **Manifest.** `features/trascrizione-con-parlanti/manifest-deltas/2026-09-25-elimina-registrazione.md`
  (AC-597…AC-636), to be folded by `build-manifest`.
- **Enforcement.**
  - `enforced_by` has two clauses:
    - presence: `5.sqm` creates `eliminazione_in_sospeso`. It is exigible from `persistenza-elimina-registrazione`.
    - prohibition: no source under `progetto/` uses a Trascrizione or Parlanti generated query. Progetto reaches the
      other contexts only through the published event.
  - The ADR 0018 prohibition (Trascrizione never uses Parlanti queries) and the ADR 0012 (b) prohibition (a
    `politiche` package never extracts) cover the two policies.
  - Discursive (code review):
    - `rimuovi` of the `registrazione` row comes after `pubblica`;
    - no file I/O happens inside `inTransazione`;
    - the Trascrizione veto re-reads inside the transaction;
    - the Documento removal goes through `abbonato-documento`'s per-key queue;
    - the pending row is concluded only after `PuliziaDerivatiRegistrazione` returns Ok.
