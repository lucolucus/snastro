package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.SQLException

/**
 * Opens (creating or migrating as needed, ADR 0006) `<cartella>/progetto.db`: WAL journal,
 * `foreign_keys=ON`, `secure_delete=ON` (ADR 0009) on every connection the driver ever opens. A
 * schema version newer than [SnastroDatabase.Schema]'s is refused with [SchemaProgettoPiuRecenteException]
 * before anything is touched: [rifiutaSeSchemaPiuRecente] probes `user_version` on a plain
 * READ-ONLY connection that never sets `journal_mode`, so a refusal never flips a rollback-journal
 * file into WAL (which itself writes the file header and creates `-wal`/`-shm`) — [driverSqlite]
 * below is only ever constructed once the version is known safe.
 */
public fun apriDatabaseProgetto(cartella: File): SnastroDatabase {
    val file = File(cartella, "progetto.db")
    rifiutaSeSchemaPiuRecente(file)
    val driver = driverSqlite("jdbc:sqlite:${file.absolutePath}")
    val db = SnastroDatabase(driver)
    try {
        allineaSchema(driver, db)
    } catch (e: SchemaProgettoPiuRecenteException) {
        driver.close()
        throw e
    } catch (e: SQLException) {
        driver.close()
        throw e
    }
    return db
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
 * `TransactionMode.IMMEDIATE` (every `BEGIN` this driver issues acquires the write lock upfront) +
 * `busy_timeout` make a concurrent writer (RiallineaImpronte, the Elaborazione queue) WAIT for the
 * single writer's lock instead of failing outright on the upgrade from a read to a write lock.
 */
internal fun driverSqlite(url: String): JdbcSqliteDriver {
    val config = SQLiteConfig().apply {
        enforceForeignKeys(true)
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        setPragma(SQLiteConfig.Pragma.SECURE_DELETE, "true")
        setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
        setBusyTimeout(BUSY_TIMEOUT_MS)
    }
    val driver = JdbcSqliteDriver(url, config.toProperties())
    driver.execute(null, "PRAGMA secure_delete = ON", 0)
    return driver
}

private const val BUSY_TIMEOUT_MS = 5_000

/**
 * AC-12: refuses a schema newer than [SnastroDatabase.Schema]'s WITHOUT ever touching the file — a
 * plain connection, opened `READONLY` (so a missing file is never created) and with no
 * `journal_mode`/`foreign_keys`/`secure_delete` pragma set (those either write the file header or
 * are irrelevant to a single read), just reads `PRAGMA user_version`. A brand-new project (no file
 * yet) has nothing to refuse.
 */
private fun rifiutaSeSchemaPiuRecente(file: File) {
    if (!file.exists()) return
    val config = SQLiteConfig().apply { setReadOnly(true) }
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", config.toProperties())
    try {
        val versioneTrovata = versioneSchema(driver)
        val versioneAttesa = SnastroDatabase.Schema.version
        if (versioneTrovata > versioneAttesa) {
            throw SchemaProgettoPiuRecenteException(versioneTrovata, versioneAttesa)
        }
    } finally {
        driver.close()
    }
}

/**
 * `versioneTrovata > versioneAttesa` is re-checked here as a defensive backstop even though
 * [rifiutaSeSchemaPiuRecente] already ruled it out before [driver] (the WAL one) was ever opened —
 * this function never runs first. `create`/`migrate` + the `user_version` write happen in ONE
 * [db] transaction: a crash between them must never leave a half-created schema at an unopenable
 * `user_version = 0`.
 */
private fun allineaSchema(driver: SqlDriver, db: SnastroDatabase) {
    val versioneAttesa = SnastroDatabase.Schema.version
    val versioneTrovata = versioneSchema(driver)
    when {
        versioneTrovata > versioneAttesa -> throw SchemaProgettoPiuRecenteException(versioneTrovata, versioneAttesa)
        versioneTrovata == 0L -> db.transaction {
            SnastroDatabase.Schema.create(driver)
            impostaVersioneSchema(driver, versioneAttesa)
        }

        versioneTrovata < versioneAttesa -> db.transaction {
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
