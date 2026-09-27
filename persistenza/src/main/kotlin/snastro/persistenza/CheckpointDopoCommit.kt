package snastro.persistenza

/**
 * ADR 0029 §3 [user B5]: registers `PRAGMA wal_checkpoint(TRUNCATE)` to run once the CURRENT transaction
 * commits (ADR 0009 / ADR 0020: purged pages must not linger in the WAL). It joins the caller's transaction
 * (the enclosing `UnitaDiLavoro.inTransazione`); on rollback the checkpoint is dropped. One checkpoint per
 * call, no dedup (user Q-1, 2026-09-25).
 */
public fun SnastroDatabase.checkpointDopoCommit() {
    transaction { afterCommit { transazioneQueries.walCheckpointTruncate() } }
}
