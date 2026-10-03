package snastro.persistenza

import app.cash.sqldelight.db.SqlDriver
import java.io.File

/**
 * A fresh PRODUCTION driver ([driverSqlite]: WAL, foreign keys, secure_delete, busy_timeout, IMMEDIATE writes,
 * DEFERRED [UnitaDiLavoroSql] reads) on the `progetto.db` of [cartella], created and migrated first exactly like the
 * app. For tests of other modules that need their own driver on a file (a connection and WAL snapshot per thread)
 * and may not import SQLDelight's JDBC driver themselves. The caller closes it.
 */
public fun apriDriverProgettoDiProva(cartella: File): SqlDriver {
    apriDatabaseProgetto(cartella).chiudi()
    return driverSqlite("jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}")
}
