package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import java.io.File

/**
 * Opens (creating or migrating as needed, ADR 0006) `<cartella>/progetto.db`: WAL journal,
 * `foreign_keys=ON`, `secure_delete=ON` (ADR 0009) on every connection the driver ever opens. A
 * schema version newer than [SnastroDatabase.Schema]'s is refused with [SchemaProgettoPiuRecenteException]
 * before anything is touched.
 */
public fun apriDatabaseProgetto(cartella: File): SnastroDatabase {
    val file = File(cartella, "progetto.db")
    val driver = driverSqlite("jdbc:sqlite:${file.absolutePath}")
    allineaSchema(driver)
    return SnastroDatabase(driver)
}

/**
 * `internal`: the test verifying the pragmas (AC-10) opens a second connection through this same
 * factory. For a file URL, SQLDelight's [JdbcSqliteDriver] uses a thread-local connection manager
 * that opens a fresh physical JDBC connection per call outside a transaction (not one connection reused
 * for the driver's lifetime) — reapplying the constructor `Properties` to each one. So
 * foreign_keys/journal_mode/secure_delete MUST be carried by [SQLiteConfig.toProperties] (below),
 * not set once after construction: a single post-construction `PRAGMA` would only ever reach the
 * one physical connection open at that instant. The explicit `PRAGMA secure_delete = ON` afterwards
 * re-asserts it on that same connection and — since `SQLiteConfig` has no dedicated secure_delete
 * setter, only the generic, non-greppable `setPragma` — is what ADR 0009's presence rule greps for.
 */
internal fun driverSqlite(url: String): JdbcSqliteDriver {
    val config = SQLiteConfig().apply {
        enforceForeignKeys(true)
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setPragma(SQLiteConfig.Pragma.SECURE_DELETE, "true")
    }
    val driver = JdbcSqliteDriver(url, config.toProperties())
    driver.execute(null, "PRAGMA secure_delete = ON", 0)
    return driver
}

private fun allineaSchema(driver: SqlDriver) {
    val versioneAttesa = SnastroDatabase.Schema.version
    val versioneTrovata = versioneSchema(driver)
    when {
        versioneTrovata > versioneAttesa -> throw SchemaProgettoPiuRecenteException(versioneTrovata, versioneAttesa)
        versioneTrovata == 0L -> {
            SnastroDatabase.Schema.create(driver)
            impostaVersioneSchema(driver, versioneAttesa)
        }

        versioneTrovata < versioneAttesa -> {
            SnastroDatabase.Schema.migrate(driver, versioneTrovata, versioneAttesa)
            impostaVersioneSchema(driver, versioneAttesa)
        }
        // else: versioneTrovata == versioneAttesa, gia allineato.
    }
}

private fun versioneSchema(driver: SqlDriver): Long =
    driver.executeQuery(null, "PRAGMA user_version", { cursore ->
        val trovata = if (cursore.next().value) cursore.getLong(0) else null
        QueryResult.Value(trovata ?: 0L)
    }, 0).value

private fun impostaVersioneSchema(driver: SqlDriver, versione: Long) {
    driver.execute(null, "PRAGMA user_version = $versione", 0)
}
