package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The PRODUCTION project database ([apriDatabaseProgetto]: WAL, secure_delete, `BEGIN IMMEDIATE`, the migrations)
 * whose driver also records every `wal_checkpoint` statement (ADR 0009 / ADR 0020 §3, AC-622/AC-634) — for
 * end-to-end tests of other modules, which may not import SQLDelight themselves (CR-3).
 *
 * [database] is the handle to pass on (e.g. `SessioneProgettoSeams.apriDatabase`); [checkpoint] holds one entry per
 * checkpoint, in order: `true` if a transaction was open on that thread when it ran.
 */
public class DatabaseProgettoContato private constructor(private val contato: DriverContato) {
    public val database: DatabaseProgetto = DatabaseProgetto(SnastroDatabase(contato), contato)

    public val checkpoint: List<Boolean> get() = contato.checkpoint.toList()

    public fun azzeraCheckpoint() {
        contato.checkpoint.clear()
    }

    public companion object {
        /** Opens [cartella]'s `progetto.db` exactly like the app, wrapping the SAME driver instance. */
        public fun apri(cartella: File): DatabaseProgettoContato {
            val reale = apriDatabaseProgetto(cartella)
            // DatabaseProgetto keeps its driver private (the app never needs it): read it, test fixtures only.
            val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
            return DatabaseProgettoContato(DriverContato(campo.get(reale) as SqlDriver))
        }
    }

    private class DriverContato(private val delegato: SqlDriver) : SqlDriver by delegato {
        val checkpoint: MutableList<Boolean> = CopyOnWriteArrayList()

        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            registra(sql)
            return delegato.execute(identifier, sql, parameters, binders)
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            registra(sql)
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }

        private fun registra(sql: String) {
            if ("wal_checkpoint" in sql) checkpoint += delegato.currentTransaction() != null
        }
    }
}
