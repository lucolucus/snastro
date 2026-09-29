package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * B20 (`UnitaDiLavoroSql.conDisattivaSolaLetturaDopo` KDoc): a plain `finally { disattivaSolaLettura() }`
 * would REPLACE [inLettura]'s block's own exception with `disattivaSolaLettura()`'s if the latter also
 * throws (standard JVM try/finally semantics) — a throwaway probe reverting to that plain `finally` makes
 * this fail with the WRONG exception surfacing (the reset failure, not [ORIGINALE]).
 */
class UnitaDiLavoroSqlDisattivaSolaLetturaTest {
    @Test
    fun `B20 un fallimento di disattivaSolaLettura non maschera l eccezione del blocco di inLettura`() {
        val driver = DriverConGuastoSuDisattiva(driverInMemoria())
        val db = SnastroDatabase(driver)
        val uow = UnitaDiLavoroSql(db)

        val lanciata = assertFailsWithThrowable { uow.inLettura<Unit> { throw ORIGINALE } }

        assertSame(ORIGINALE, lanciata, "l'eccezione del blocco resta quella che il chiamante vede")
        val soppressa = lanciata.suppressed.singleOrNull()
        assertNotNull(soppressa, "il fallimento di disattivaSolaLettura e' allegato, non perso: ${lanciata.suppressed}")
        assertTrue("disattiva" in (soppressa.message ?: ""), "${soppressa.message}")
    }

    private fun assertFailsWithThrowable(blocco: () -> Unit): Throwable = try {
        blocco()
        fail("il blocco doveva lanciare $ORIGINALE")
    } catch (e: Throwable) {
        e
    }

    /** Fails ONLY `PRAGMA query_only = 0` (`disattivaSolaLettura`); every other statement passes through. */
    private class DriverConGuastoSuDisattiva(private val delegato: SqlDriver) : SqlDriver by delegato {
        override fun execute(
            identifier: Int?,
            sql: String,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<Long> {
            if ("query_only = 0" in sql) error("disattivaSolaLettura fallita (simulata)")
            return delegato.execute(identifier, sql, parameters, binders)
        }
    }

    private companion object {
        val ORIGINALE = IllegalStateException("boom nel blocco di inLettura")

        fun driverInMemoria(): SqlDriver =
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { SnastroDatabase.Schema.create(it) }
    }
}
