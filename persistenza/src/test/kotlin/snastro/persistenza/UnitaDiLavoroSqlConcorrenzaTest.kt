package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * MUST-FIX-2 (rework cycle 1, code-review HIGH): one [UnitaDiLavoroSql] instance is shared by the
 * UI commands and the background writers (RiallineaImpronte, the Elaborazione queue), and
 * SQLDelight's [driverSqlite] (a FILE-backed [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver])
 * hands out ONE transaction PER THREAD. Two threads each running their own outer [inTransazione]
 * must never share nesting/condemnation state: thread B's `Errore` must not roll back thread A's
 * already-open, unrelated outer transaction, and each commits/rolls back independently. Before the
 * fix (plain instance fields on [UnitaDiLavoroSql]) this test failed: A's own `Ok` block was
 * reported as B's `Errore` and A's committed write vanished.
 */
class UnitaDiLavoroSqlConcorrenzaTest {
    @Test
    fun `l Errore del thread B non condanna la transazione esterna gia aperta dal thread A`(
        @TempDir cartella: Path,
    ) {
        val driver: SqlDriver = driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db").absolutePathString()}")
        SnastroDatabase.Schema.create(driver)
        driver.execute(null, "CREATE TABLE effetto_di_prova(valore TEXT NOT NULL)", 0)
        val uow = UnitaDiLavoroSql(SnastroDatabase(driver))

        val partenza = CyclicBarrier(2)
        val aDentroLaPropriaTransazione = CountDownLatch(1)
        val esitoA = AtomicReference<Esito<Unit>>()
        val esitoB = AtomicReference<Esito<Unit>>()

        // Thread A opens its OWN outer transaction, signals it is inside it, then keeps it open
        // for a while (simulating a long-running background writer) before committing.
        val threadA = thread(start = false, name = "A-scrittore-in-background") {
            partenza.await()
            esitoA.set(
                uow.inTransazione<Unit> {
                    aDentroLaPropriaTransazione.countDown()
                    Thread.sleep(300)
                    scrivi(driver, "esterno-a")
                    Esito.Ok(Unit)
                },
            )
        }
        // Thread B starts its OWN outer transaction only once A is confirmed inside its own — the
        // exact overlap that condemned A under the shared-field bug — and fails immediately (no DB
        // write of its own: nothing to wait for A's write lock, so no deadlock either way).
        val threadB = thread(start = false, name = "B-comando-ui") {
            partenza.await()
            aDentroLaPropriaTransazione.await()
            esitoB.set(
                uow.inTransazione<Unit> { Esito.Errore(ERRORE_B) },
            )
        }

        threadA.start()
        threadB.start()
        threadA.join(15_000)
        threadB.join(15_000)

        assertIs<Esito.Ok<Unit>>(esitoA.get(), "l'Ok di A non deve diventare l'Errore di B")
        assertEquals(ERRORE_B, assertIs<Esito.Errore>(esitoB.get()).errore)
        assertEquals(setOf("esterno-a"), effetti(driver), "solo la transazione di A deve aver committato")
    }

    private fun scrivi(driver: SqlDriver, valore: String) {
        driver.execute(null, "INSERT INTO effetto_di_prova(valore) VALUES (?)", 1) {
            bindString(0, valore)
        }
    }

    private fun effetti(driver: SqlDriver): Set<String> =
        driver.executeQuery(null, "SELECT valore FROM effetto_di_prova", { cursore ->
            val valori = mutableListOf<String>()
            while (cursore.next().value) valori += checkNotNull(cursore.getString(0))
            QueryResult.Value(valori)
        }, 0).value.toSet()

    private companion object {
        val ERRORE_B = ErroreDiProva.Fallito("b")
    }
}
