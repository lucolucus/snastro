package snastro.persistenza

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.sqlite.SQLiteConfig

/**
 * A fresh in-memory [SnastroDatabase] at the current schema version, foreign keys enforced — for
 * repository/round-trip tests (dev-architecture-app.md#repository). WAL/secure_delete are file-only
 * pragmas (no effect on `:memory:`) so they are not set here; [apriDatabaseProgetto] sets them.
 */
public fun databaseInMemoria(): SnastroDatabase {
    val config = SQLiteConfig().apply { enforceForeignKeys(true) }
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
    SnastroDatabase.Schema.create(driver)
    return SnastroDatabase(driver)
}
