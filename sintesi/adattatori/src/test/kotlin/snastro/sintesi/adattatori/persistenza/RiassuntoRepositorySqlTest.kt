package snastro.sintesi.adattatori.persistenza

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteException
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.sintesi.applicazione.porte.PredisposizioneSintesi
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryContratto
import snastro.sintesi.applicazione.porte.unRiassunto
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * D2 (dev-architecture-app.md#porta-contratto): [RiassuntoRepositoryContratto] against
 * [RiassuntoRepositorySql] on a fresh in-memory [SnastroDatabase] (AC-S111). [predisponi] seeds the
 * contract's parent rows with raw SQL (never `progettoQueries`/`registrazioneQueries` — ADR 0021
 * clause 2). AC-S112's index -> `Errore` half is already the inherited `AC-S66` tests; only the
 * "any OTHER constraint fault is rethrown raw" half (ADR 0003) needs its own test below, since the
 * Finta has no concept of a real foreign key.
 */
class RiassuntoRepositorySqlTest : RiassuntoRepositoryContratto() {
    private lateinit var driver: SqlDriver

    override fun repository(): RiassuntoRepository {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        return RiassuntoRepositorySql(SnastroDatabase(driver))
    }

    override fun predisponi(predisposizione: PredisposizioneSintesi) {
        semina(driver, predisposizione)
    }

    @Test
    fun `AC-S112 un vincolo diverso dagli indici parziali non e mappato e arriva grezzo`() {
        // No registrazione seeded: the immediate FK riassunto.registrazione_id -> registrazione(id) refuses it,
        // a constraint the Finta (RiassuntoRepositoryFinta) has no concept of.
        val repo = RiassuntoRepositorySql(databaseInMemoria())

        assertFailsWith<SQLiteException> {
            repo.salva(unRiassunto("riassunto-1", RiassuntoRepositoryContratto.REGISTRAZIONE))
        }
    }
}
