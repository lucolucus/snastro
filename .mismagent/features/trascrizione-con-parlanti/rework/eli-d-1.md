# Rework eli-d — cycle 1 (2026-09-26)
Source: verifier FAIL on a07755c (code-review APPROVE, rated the same gap MED). FAIL only.

## FAIL-1 — AC-634 / INV-28: "exactly 3 wal_checkpoint(TRUNCATE), all after the commit" not proven end-to-end
- User decision Q-1 settled exactly this clause; the per-removal unit test (AC-622, eli-b) runs on an in-memory driver without the production DriverSqliteImmediato nor the dispatcher-wrapped unit of work, and does not pin the policy's call sequence.
- CR-3 is NOT an obstacle: add a counting helper in `:persistenza` testFixtures (e.g. `apriDatabaseProgettoContato(cartella)`) that wraps the PRODUCTION driver like eli-b's DriverContato and exposes a plain `List<Boolean>` ("transaction open at checkpoint time"); SQLDelight/JDBC imports stay in `:persistenza`. Inject it through the existing `SessioneProgettoSeams.apriDatabase` (AmbienteR2 already passes seams).
- Test: in EliminaRegistrazioneR2Test AC-634, clear the counter right before `confermaElimina`, then after the after-commit work assert exactly `listOf(false, false, false)`.
