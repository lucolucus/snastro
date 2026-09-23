package snastro.persistenza

import app.cash.sqldelight.db.SqlDriver

/**
 * The open handle [apriDatabaseProgetto] returns: [database] for queries/transactions, [chiudi] to
 * merge the WAL back into `progetto.db`. With SQLDelight 2.1.0, a `JdbcSqliteDriver` opened on a
 * FILE (never an in-memory one, cf. `driverSqlite`) is backed by a `ThreadedConnectionManager`: it
 * opens a FRESH physical JDBC connection per call outside a transaction and closes that same
 * connection right after the call — there is no single long-lived connection for [driver]'s own
 * `close()` to release (`ThreadedConnectionManager.close()` is a documented no-op). So [chiudi] is
 * really just `PRAGMA wal_checkpoint(TRUNCATE)` run on one such fresh, short-lived connection —
 * synchronously merging the WAL and truncating `-wal`/removing `-shm` — never SQLite's own "last
 * connection closes" auto-checkpoint (which this driver never triggers, since no connection is ever
 * kept open long enough to BE a last one). `driver.close()` is still called afterwards for symmetry
 * with the [SqlDriver] contract, even though it does nothing today. `:avvio` calls [chiudi] in
 * `SessioneProgettoImpl.chiudi` and on every `crea`/`apri` failure path reached AFTER the database
 * was already open, so a project is never left with the WAL un-merged.
 */
public class DatabaseProgetto(public val database: SnastroDatabase, private val driver: SqlDriver) {
    public fun chiudi() {
        driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
        driver.close()
    }
}
