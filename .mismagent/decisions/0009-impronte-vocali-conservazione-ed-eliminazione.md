---
scope: global
status: accepted
supersedes: null
closes_spike: null
enforced_by:
  kind: presence
  rule: "grep -rniE --include='*.kt' --exclude-dir=build '(secure_delete[[:space:]]*=[[:space:]]*(on|1|true)|setSecureDelete\\(true\\))' persistenza | grep -vE '^[^:]*:[0-9]+:[[:space:]]*(//|\\*|/\\*)' | grep -q ."
  exigible_from: "the persistenza driver-factory block (scaffold or first persistence block — id pinned by build-manifest)"
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
