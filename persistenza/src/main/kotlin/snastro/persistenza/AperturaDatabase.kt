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

/** `internal`: the test verifying the pragmas (AC-10) opens a second connection through this same factory. */
internal fun driverSqlite(url: String): JdbcSqliteDriver {
    val config = SQLiteConfig().apply {
        enforceForeignKeys(true)
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setPragma(SQLiteConfig.Pragma.SECURE_DELETE, "true")
    }
    return JdbcSqliteDriver(url, config.toProperties())
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
