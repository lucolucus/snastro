package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.UnitaDiLavoroContratto
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * LOW (rework cycle 1): [UnitaDiLavoroSqlTest] runs the kernel's contract against an in-memory
 * driver, which SQLDelight's [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver] serves from
 * a single reused connection (not one per thread). This subclass runs the SAME contract against a
 * real FILE-backed [driverSqlite] — the `ThreadedConnectionManager` path [UnitaDiLavoroSql]
 * actually runs on in the app (AC-11). The harness table (`effetto_di_prova`) is domain-agnostic on
 * purpose, created directly on the same [SqlDriver] `UnitaDiLavoroSql` transacts on.
 */
class UnitaDiLavoroSqlFileTest : UnitaDiLavoroContratto() {
    @TempDir
    lateinit var cartella: Path

    override fun ambiente(): Ambiente {
        val driver: SqlDriver = driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}")
        SnastroDatabase.Schema.create(driver)
        driver.execute(null, "CREATE TABLE effetto_di_prova(valore TEXT NOT NULL)", 0)
        val db = SnastroDatabase(driver)
        return object : Ambiente {
            override val unitaDiLavoro = UnitaDiLavoroSql(db)

            override fun scrivi(effetto: String) {
                driver.execute(null, "INSERT INTO effetto_di_prova(valore) VALUES (?)", 1) {
                    bindString(0, effetto)
                }
            }

            override fun effetti(): Set<String> =
                driver.executeQuery(null, "SELECT valore FROM effetto_di_prova", { cursore ->
                    val valori = mutableListOf<String>()
                    while (cursore.next().value) valori += checkNotNull(cursore.getString(0))
                    QueryResult.Value(valori)
                }, 0).value.toSet()
        }
    }
}
