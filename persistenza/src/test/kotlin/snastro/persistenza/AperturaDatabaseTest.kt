package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AperturaDatabaseTest {
    @Test
    fun `AC-10 apriDatabaseProgetto apre il DB con journal WAL foreign_keys ON e secure_delete ON`(
        @TempDir cartella: Path,
    ) {
        apriDatabaseProgetto(cartella.toFile())

        // Same factory apriDatabaseProgetto uses (driverSqlite): foreign_keys/secure_delete are
        // per-connection pragmas carried by the driver's Properties, so any connection it opens —
        // this one included — must show them ON, exactly like every thread's own connection would.
        val driver = driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}")
        assertEquals("wal", pragma(driver, "journal_mode"))
        assertEquals("1", pragma(driver, "foreign_keys"))
        assertEquals("1", pragma(driver, "secure_delete"))
    }

    @Test
    fun `AC-12 uno schema piu recente di quello supportato viene rifiutato senza modificare il file`(
        @TempDir cartella: Path,
    ) {
        val file = File(cartella.toFile(), "progetto.db")
        val driverSetup = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        SnastroDatabase.Schema.create(driverSetup)
        driverSetup.execute(null, "PRAGMA user_version = 999", 0)
        driverSetup.close()
        val contenutoPrima = file.readBytes()

        val eccezione = assertFailsWith<SchemaProgettoPiuRecenteException> {
            apriDatabaseProgetto(cartella.toFile())
        }

        assertEquals(999L, eccezione.versioneTrovata)
        assertEquals(SnastroDatabase.Schema.version, eccezione.versioneSupportata)
        assertTrue(eccezione.message?.isNotBlank() == true, "il messaggio deve essere chiaro")
        // Byte-for-byte, not just length: a refusal must never flip the file into WAL (which writes
        // the header) even when the length happens to stay the same.
        assertContentEquals(contenutoPrima, file.readBytes(), "il file non deve essere modificato")
        assertFalse(File(cartella.toFile(), "progetto.db-wal").exists(), "nessun file -wal deve comparire")
        assertFalse(File(cartella.toFile(), "progetto.db-shm").exists(), "nessun file -shm deve comparire")

        val driverVerifica = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        assertEquals("999", pragma(driverVerifica, "user_version"))
    }

    private fun pragma(driver: JdbcSqliteDriver, nome: String): String =
        driver.executeQuery(null, "PRAGMA $nome", { cursore ->
            check(cursore.next().value) { "PRAGMA $nome non restituisce righe" }
            QueryResult.Value(checkNotNull(cursore.getString(0)))
        }, 0).value
}
