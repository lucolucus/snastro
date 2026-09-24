package snastro.persistenza

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.ConnectionManager.Transaction
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.Connection
import java.sql.SQLException

/**
 * fix-batch-17: SQLDelight 2.1.0's [JdbcSqliteDriver] opens every transaction with a literal
 * `BEGIN TRANSACTION` (`JdbcSqliteDriverConnectionManager.beginTransaction`, a plain prepared
 * statement) — never through JDBC `setAutoCommit(false)`, the only path where sqlite-jdbc applies
 * `SQLiteConfig.TransactionMode`. So `TransactionMode.IMMEDIATE` was inert and every transaction was
 * DEFERRED: in WAL, a read-then-write transaction whose snapshot went stale (another thread committed
 * in between) failed its write with `SQLITE_BUSY_SNAPSHOT`, which busy_timeout cannot absorb.
 *
 * This wrapper keeps [base]'s connection management verbatim (the per-thread
 * `ThreadedConnectionManager`, its transaction slot and query listeners — all delegated) and only
 * replaces the three transaction statements, so the outermost transaction starts with
 * `BEGIN IMMEDIATE`: the write lock is taken up front (waiting up to busy_timeout), never upgraded
 * from a read snapshot later. SQLDelight never calls [beginTransaction] for a nested transaction.
 */
internal class DriverSqliteImmediato(private val base: JdbcSqliteDriver) : JdbcDriver() {
    override fun getConnection(): Connection = base.getConnection()

    override fun closeConnection(connection: Connection) = base.closeConnection(connection)

    override var transaction: Transaction?
        get() = base.transaction
        set(value) {
            base.transaction = value
        }

    /**
     * Unlike a DEFERRED `BEGIN`, `BEGIN IMMEDIATE` CAN fail (write lock still busy after busy_timeout).
     * SQLDelight has already stored the new transaction in this thread's slot by then and never clears
     * it on that path: left there, every later "transaction" on this (pooled) thread would look nested
     * and run in autocommit. Clearing it (outermost only, so `null`) also closes the connection.
     */
    override fun Connection.beginTransaction() {
        try {
            esegui("BEGIN IMMEDIATE TRANSACTION")
        } catch (e: SQLException) {
            transaction = null
            throw e
        }
    }

    override fun Connection.endTransaction() = esegui("END TRANSACTION")

    override fun Connection.rollbackTransaction() = esegui("ROLLBACK TRANSACTION")

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) =
        base.addListener(*queryKeys, listener = listener)

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) =
        base.removeListener(*queryKeys, listener = listener)

    override fun notifyListeners(vararg queryKeys: String) = base.notifyListeners(*queryKeys)

    override fun close() = base.close()

    private fun Connection.esegui(sql: String) {
        prepareStatement(sql).use { it.execute() }
    }
}
