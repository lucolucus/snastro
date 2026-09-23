package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import snastro.kernel.UnitaDiLavoroContratto

/**
 * AC-11: [UnitaDiLavoroSql] passes the kernel's [UnitaDiLavoroContratto] against a real SQLite
 * connection. The harness table (`effetto_di_prova`) is domain-agnostic on purpose — created
 * directly on the same [SqlDriver] `UnitaDiLavoroSql` transacts on, never through the real v1
 * schema/snapshot (this is an infrastructure contract, not a round-trip of a domain table).
 */
class UnitaDiLavoroSqlTest : UnitaDiLavoroContratto() {
    override fun ambiente(): Ambiente {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SnastroDatabase.Schema.create(driver)
        driver.execute(null, "CREATE TABLE effetto_di_prova(valore TEXT NOT NULL)", 0)
        val db = SnastroDatabase(driver)
        return object : Ambiente {
            override val unitaDiLavoro = UnitaDiLavoroSql(db)

            override fun scrivi(effetto: String) {
                driver.execute(null, "INSERT INTO effetto_di_prova(valore) VALUES (?)", 1) {
                    bindString(0, effetto)
                }
            }

            override fun effetti(): Set<String> =
                driver.executeQuery(null, "SELECT valore FROM effetto_di_prova", { cursore ->
                    val valori = mutableListOf<String>()
                    while (cursore.next().value) valori += checkNotNull(cursore.getString(0))
                    QueryResult.Value(valori)
                }, 0).value.toSet()
        }
    }
}
