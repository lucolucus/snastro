package snastro.persistenza

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Writes `progetto.db` of [cartella] as the release before `incontro` left it: schema 7 (`1.sqm`...`6.sqm`), then
 * [righe] (plain INSERTs in the schema-7 shapes). Any previous database file is replaced. Opening the folder through
 * [apriDatabaseProgetto] migrates it to the current schema — for end-to-end tests of other modules, which may not
 * import SQLDelight themselves (CR-3).
 */
public fun scriviDatabaseV7(cartella: File, righe: List<String>) {
    listOf("progetto.db", "progetto.db-wal", "progetto.db-shm").forEach { File(cartella, it).delete() }
    val driver = JdbcSqliteDriver("jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}")
    try {
        SnastroDatabase.Schema.migrate(driver, 1L, VERSIONE_SCHEMA_PRE_INCONTRO)
        driver.execute(null, "PRAGMA user_version = $VERSIONE_SCHEMA_PRE_INCONTRO", 0)
        righe.forEach { driver.execute(null, it, 0) }
    } finally {
        driver.close()
    }
}

private const val VERSIONE_SCHEMA_PRE_INCONTRO = 7L
