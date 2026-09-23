package snastro.persistenza

import app.cash.sqldelight.db.SqlDriver

/**
 * The open handle [apriDatabaseProgetto] returns: [database] for queries/transactions, [chiudi] to
 * release the JDBC connection(s) the driver holds. `PRAGMA wal_checkpoint(TRUNCATE)` runs FIRST,
 * synchronously merging the WAL back into `progetto.db` and truncating `-wal`/removing `-shm` —
 * SQLite's own "last connection closes" auto-checkpoint is not reliably synchronous with the JVM
 * call returning (observed: an intermittent `-wal` file still present right after `driver.close()`),
 * so this makes the cleanup deterministic instead of relying on it. `:avvio` calls [chiudi] in
 * `SessioneProgettoImpl.chiudi` and on every `crea`/`apri` failure path reached AFTER the database
 * was already open, so a project is never left with a dangling connection.
 */
public class DatabaseProgetto(public val database: SnastroDatabase, private val driver: SqlDriver) {
    public fun chiudi() {
        driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
        driver.close()
    }
}
