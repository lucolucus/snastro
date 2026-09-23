---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by:
  kind: presence
  rule: "grep -rniE --include='*.kt' --exclude-dir=build '(secure_delete[[:space:]]*=[[:space:]]*(on|1|true)|setSecureDelete\\(true\\))' persistenza | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
  exigible_from: "persistenza-schema"
amended: 2026-09-23   # see "Amendment 2026-09-23 (b)" — R12 superseded by ADR 0012 Amendment (b)
---
# 0009 — `ImprontaVocale` (biometric): stored only in the project DB, purged in the tombstone transaction

## Context
`ImprontaVocale` is third-party biometric data. `Eliminazione del Parlante` is a privacy right:
all its prints go, the `Nome` survives as a tombstone ([INV-13]). The user decided (2026-09-23): no
in-app encryption (FileVault), and **accepts that their own backups (Time Machine, copies of the
project folder) may retain purged prints** — outside the app's control.

## Decision
- Prints are stored **only** as `BLOB` (little-endian float32) rows of `impronta_vocale` in the
  project's `progetto.db`. The app writes **no other copy**: no file export, no cache, no log of
  embedding values, no in-app backup. The transient embedding of a not-yet-attributed `Voce` used
  for a `Proposta` lives in memory only ([INV-15]: a `Proposta` never writes the `Galleria`).
- `EliminaParlante` deletes every `impronta_vocale` row of the `Parlante` **in the same
  transaction** that sets `StatoParlante = eliminato`; then the adapter runs
  `PRAGMA wal_checkpoint(TRUNCATE)` so the deleted pages do not linger in the WAL.
- The connection runs with **`secure_delete=ON`** (freed pages are zeroed) — presence rule
  (enforced_by), set in `:persistenza`'s driver factory.
- Every print removal path ([INV-15] attribution change, [INV-21] revisione policy, [INV-25]
  occasionale ceasing to exist) uses the same deletion — no soft-delete for prints (the aggregate's
  "no deletion" rule applies to `Parlante`, not to the biometric rows it owns).
- **Documented caveat (user-accepted):** copies of the project folder made outside the app (Time
  Machine, manual backups, sync services) may still contain prints purged later. The deletion
  dialog of S4 does not promise otherwise; the README/licence screen states it.

## Consequences
- Tests: invariant-test on the `parlante` aggregate ([INV-13]) + adapter round-trip test asserting
  zero `impronta_vocale` rows after `EliminaParlante`.

## Amendment 2026-09-23 (build-manifest reconciliation R19, R23, R12)
- `exigible_from` pinned to block **`persistenza-schema`** (owns `apriDatabaseProgetto` with `secure_delete=ON`).
- R23: `PRAGMA wal_checkpoint(TRUNCATE)` cannot run inside a transaction — `repository-sql-parlanti`
  runs it **after the commit** of the `EliminaParlante` transaction (the purge itself stays in-tx).
- R12: *(superseded by ADR 0012 "Amendment 2026-09-23 (b)" — kept as history)* the embedding of a
  `Voce` being attributed (INV-15/INV-21 re-derivation) is extracted inside the command transaction
  and written only as `impronta_vocale` rows; the transient `Proposta` embedding stays in memory
  only (unchanged).

## Amendment 2026-09-23 (b) — prints under ADR 0012 option (c) [user]
- **R12 replaced (ADR 0012 Amendment (b)).** The embedding of a `Voce` being attributed is
  extracted **before** the command's transaction, from the bounded `SorgenteImpronta` only (≤
  `BUDGET_IMPRONTA_MS`, provisional 30 000 ms), and written **only** as an `impronta_vocale` row in
  that transaction; re-derivations after a `Revisione` run after commit (`RiallineaImpronte`). The
  in-memory-only rule is unchanged for the transient `Proposta` embedding and now extends to every
  pre-transaction embedding: an extracted `Impronta` that is not written (`VoceCambiata`, a refused
  command, a failed compare-and-set) is simply dropped — never cached, logged or retried from disk.
- **Two non-biometric columns** on `impronta_vocale`: `sorgente_impronta TEXT NOT NULL` (the
  `SorgenteImpronta.chiave`, interval timestamps only) and `modello_impronta TEXT NOT NULL` (the
  extractor's model id). They are purged with the row by every removal path above.
- **No resurrection.** `RiallineaImpronte` only UPDATEs an existing row under compare-and-set and
  never INSERTs, so a print purged by `EliminaParlante`, by a changed `Attribuzione` or by [INV-25]
  cannot be re-created by after-commit work that started before the purge.
- **`unire` inheritance onto an `eliminato` `Parlante`** (ADR 0012 Amendment (b) point 4): only the
  tombstone `Attribuzione` is re-keyed to the surviving `Voce`; **no** `impronta_vocale` row is
  created — [INV-13]'s "zero prints" holds; the exception concerns the `Attribuzione` only.
