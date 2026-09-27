package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.absolutePathString

/**
 * The production file driver ([driverSqlite]: WAL, busy_timeout, [DriverSqliteImmediato]) with every statement
 * traced in order into [istruzioni]: the transaction statements (via [DriverSqliteImmediato]'s observer, where
 * [guasto] may throw to inject a fault) and every query/pragma ([Tracciante]). Plus the domain-agnostic
 * `effetto_di_prova` table the transaction contracts write to (an infrastructure contract, not a domain table).
 */
internal class DatabaseTracciato(cartella: Path, guasto: (String) -> Unit = {}) {
    val istruzioni: MutableList<String> = CopyOnWriteArrayList()

    val driver: SqlDriver = Tracciante(
        driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}") {
            istruzioni += it
            guasto(it)
        },
        istruzioni,
    )

    val db: SnastroDatabase = SnastroDatabase(driver)

    val uow: UnitaDiLavoroSql = UnitaDiLavoroSql(db)

    init {
        driver.execute(null, "CREATE TABLE effetto_di_prova(valore TEXT NOT NULL)", 0)
        istruzioni.clear()
    }

    fun scrivi(effetto: String) {
        driver.execute(null, "INSERT INTO effetto_di_prova(valore) VALUES (?)", 1) { bindString(0, effetto) }
    }

    fun effetti(): List<String> =
        driver.executeQuery(null, "SELECT valore FROM effetto_di_prova ORDER BY valore", { cursore ->
            val valori = mutableListOf<String>()
            while (cursore.next().value) valori += checkNotNull(cursore.getString(0))
            QueryResult.Value(valori)
        }, 0).value

    /** The transaction statements only (BEGIN / END / ROLLBACK), in order. */
    fun transazioni(): List<String> = istruzioni.filter { it.endsWith(" TRANSACTION") }

    private class Tracciante(private val delegato: SqlDriver, private val istruzioni: MutableList<String>) :
        SqlDriver by delegato {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            istruzioni += sql
            return delegato.execute(identifier, sql, parameters, binders)
        }

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            istruzioni += sql
            return delegato.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }
}
