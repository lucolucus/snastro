package snastro.persistenza

/**
 * ADR 0029 §3 [user B5]: registers `PRAGMA wal_checkpoint(TRUNCATE)` to run once the CURRENT transaction
 * commits (ADR 0009 / ADR 0020: purged pages must not linger in the WAL). It joins the caller's transaction
 * (the enclosing `UnitaDiLavoro.inTransazione`); on rollback the checkpoint is dropped. One checkpoint per
 * call, no dedup (user Q-1, 2026-09-25).
 *
 * B23: "joins the caller's transaction" holds only for the intended call site — inside a
 * `UnitaDiLavoro.inTransazione` (every production caller today, `ParlanteRepositorySql.salva`/`rimuovi`).
 * Two calls this function does NOT guard against, pinned by `CheckpointDopoCommitTest`:
 * - **Outside any unit** (no enclosing transaction at all): the raw SQLDelight `transaction { }` call below
 *   opens its OWN fresh `BEGIN IMMEDIATE` and ends it right there — the checkpoint still runs exactly once,
 *   just in a transaction of its own, never "joining" anything.
 * - **Inside a `LetturaCoerente.inLettura`:** it DOES join that read transaction (a plain nested `transaction`,
 *   not `noEnclosing`), but its `afterCommit` only fires once the READ itself ends (`END TRANSACTION`) — the
 *   checkpoint runs AFTER the read is over, never inside the snapshot it read.
 *

 * D-0014 (pre-R3-4 B17): the pragma's own row (busy/log/checkpointed) can never be read off SQLDelight's
 * generated `TransazioneQueries.walCheckpointTruncate()` — SQLDelight 2.1.0's `jdbc-driver`
 * (`JdbcPreparedStatement.execute()`) hardcodes `0L` whenever the prepared statement returns a result set
 * (`PRAGMA wal_checkpoint` always does), discarding it before this function is ever reached — confirmed
 * against the generated source (`TransazioneQueries.walCheckpointTruncate(): QueryResult<Long>`, a plain
 * `driver.execute`, never `executeQuery`). `:persistenza` has no `:supporto` edge (ADR 0028 §5) to own a
 * retry worker itself, so [walTroncato] — supplied by the caller, which DOES know the project's `-wal` file
 * path (the same file-size check `ParlanteRepositorySqlCheckpointTest` already makes) — is asked right after
 * the checkpoint; [seIncompleto] runs ONLY when it reports the WAL was not (fully) truncated, e.g. an open
 * DEFERRED reader blocked it (ADR 0009/0020). Both default to preserving today's fire-and-forget behaviour
 * for callers that pass neither.
 */
public fun SnastroDatabase.checkpointDopoCommit(walTroncato: () -> Boolean = { true }, seIncompleto: () -> Unit = {}) {
    transaction {
        afterCommit {
            transazioneQueries.walCheckpointTruncate()
            if (!walTroncato()) seIncompleto()
        }
    }
}
