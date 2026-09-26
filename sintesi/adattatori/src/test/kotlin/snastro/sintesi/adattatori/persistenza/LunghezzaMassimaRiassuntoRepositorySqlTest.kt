package snastro.sintesi.adattatori.persistenza

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import snastro.persistenza.SnastroDatabase
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepositoryContratto
import snastro.sintesi.applicazione.porte.PredisposizioneSintesi

/**
 * D2 (dev-architecture-app.md#porta-contratto): [LunghezzaMassimaRiassuntoRepositoryContratto] against
 * [LunghezzaMassimaRiassuntoRepositorySql] on a fresh in-memory [SnastroDatabase] (AC-S111/AC-S70).
 * [predisponi] seeds the contract's Progetto rows with raw SQL (never `progettoQueries` — ADR 0021
 * clause 2).
 */
class LunghezzaMassimaRiassuntoRepositorySqlTest : LunghezzaMassimaRiassuntoRepositoryContratto() {
    private lateinit var driver: SqlDriver

    override fun repository(): LunghezzaMassimaRiassuntoRepository {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        return LunghezzaMassimaRiassuntoRepositorySql(SnastroDatabase(driver))
    }

    override fun predisponi(predisposizione: PredisposizioneSintesi) {
        semina(driver, predisposizione)
    }
}
