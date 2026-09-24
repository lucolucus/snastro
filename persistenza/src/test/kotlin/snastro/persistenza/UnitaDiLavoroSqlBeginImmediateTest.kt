package snastro.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import java.nio.file.Path
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * fix-batch-17 (avvio-parlanti finding): a Parlanti command (ConfermaAttribuzione: READ, then
 * WRITE) failed with `SQLITE_BUSY_SNAPSHOT` because the pipeline's completion commit landed on
 * another thread between its read and its write. In WAL mode that is exactly what a DEFERRED
 * transaction does: the read pins a snapshot, a concurrent commit makes it stale, and the upgrade
 * to a write lock fails immediately (busy_timeout cannot help — waiting never refreshes the
 * snapshot). Every [UnitaDiLavoroSql] transaction must start with `BEGIN IMMEDIATE`: then thread B's
 * write WAITS (busy_timeout) for A's commit instead, and both succeed, B last.
 *
 * Real threads on a real file-backed database opened through [apriDatabaseProgetto] — the path the
 * app runs on.
 */
class UnitaDiLavoroSqlBeginImmediateTest {
    @Test
    fun `una lettura poi scrittura non fallisce se un altro thread committa in mezzo`(@TempDir cartella: Path) {
        val progetto = apriDatabaseProgetto(cartella.toFile())
        val db = progetto.database
        db.progettoQueries.inserisci(ID, "iniziale")
        val uow = UnitaDiLavoroSql(db)

        val aHaLetto = CountDownLatch(1)
        val bHaCommittato = CountDownLatch(1)
        val esitoA = AtomicReference<Esito<Unit>>()
        val esitoB = AtomicReference<Esito<Unit>>()
        val guastoA = AtomicReference<Throwable>()
        val guastoB = AtomicReference<Throwable>()

        val threadA = thread(name = "A-comando-parlanti") {
            runCatching {
                uow.inTransazione<Unit> {
                    db.progettoQueries.trova().executeAsOne()
                    aHaLetto.countDown()
                    // DEFERRED: B commits right away (A only holds a WAL read snapshot). IMMEDIATE:
                    // B is parked in busy_timeout behind A's write lock, so this just times out.
                    bHaCommittato.await(ATTESA_B_MS, TimeUnit.MILLISECONDS)
                    db.progettoQueries.aggiorna(nome = "a", id = ID)
                    Esito.Ok(Unit)
                }
            }.onSuccess(esitoA::set).onFailure(guastoA::set)
        }
        val threadB = thread(name = "B-pipeline-completamento") {
            aHaLetto.await()
            runCatching {
                uow.inTransazione<Unit> {
                    db.progettoQueries.aggiorna(nome = "b", id = ID)
                    Esito.Ok(Unit)
                }
            }.onSuccess(esitoB::set).onFailure(guastoB::set)
            bHaCommittato.countDown()
        }
        threadA.join(15_000)
        threadB.join(15_000)

        try {
            assertNull(guastoA.get(), "A (lettura poi scrittura) non deve fallire: ${guastoA.get()}")
            assertNull(guastoB.get(), "B non deve fallire: ${guastoB.get()}")
            assertIs<Esito.Ok<Unit>>(esitoA.get())
            assertIs<Esito.Ok<Unit>>(esitoB.get())
            assertEquals("b", db.progettoQueries.trova().executeAsOne().nome, "B attende il commit di A, poi scrive")
        } finally {
            progetto.chiudi()
        }
    }

    /**
     * `BEGIN IMMEDIATE` can fail once busy_timeout expires (a DEFERRED `BEGIN` never did): the failed
     * transaction must not stay parked in the thread's slot, or the next transaction on that same thread
     * would run "nested" in autocommit and an `Errore` could no longer roll its writes back.
     */
    @Test
    fun `un BEGIN IMMEDIATE scaduto non lascia il thread in una transazione fantasma`(@TempDir cartella: Path) {
        val progetto = apriDatabaseProgetto(cartella.toFile())
        val db = progetto.database
        val uow = UnitaDiLavoroSql(db)
        val altro = SnastroDatabase(driverSqlite("jdbc:sqlite:${cartella.resolve("progetto.db")}"))
        val bloccoPreso = CountDownLatch(1)
        val rilascia = CountDownLatch(1)
        val bloccante = thread(name = "scrittore-lungo") {
            altro.transaction {
                bloccoPreso.countDown()
                rilascia.await()
            }
        }
        try {
            bloccoPreso.await()
            assertFailsWith<SQLException> { uow.inTransazione<Unit> { Esito.Ok(Unit) } }
            rilascia.countDown()
            bloccante.join(15_000)

            val esito = uow.inTransazione<Unit> {
                db.progettoQueries.inserisci(ID, "da annullare")
                Esito.Errore(ErroreDiProva.Fallito("rollback"))
            }

            assertIs<Esito.Errore>(esito)
            assertNull(db.progettoQueries.trova().executeAsOneOrNull(), "l'Errore deve annullare la scrittura")
        } finally {
            rilascia.countDown()
            progetto.chiudi()
        }
    }

    private companion object {
        const val ID = "p1"
        const val ATTESA_B_MS = 1_000L
    }
}
