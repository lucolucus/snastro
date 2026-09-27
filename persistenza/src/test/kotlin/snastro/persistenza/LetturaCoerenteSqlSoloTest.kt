package snastro.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.supporto.test.attendiFinche
import java.nio.file.Path
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0029 §2–§4, SQL only: the statements [UnitaDiLavoroSql] and [DriverSqliteImmediato] issue for each rule,
 * the snapshot (case 8) and the absence of queueing behind a writer (case 9), on the production file driver.
 */
class LetturaCoerenteSqlSoloTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-C15 la lettura esterna apre BEGIN DEFERRED con query_only e lo azzera prima di chiudere`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inLettura { sql.effetti() }

        assertEquals(
            listOf(
                "BEGIN DEFERRED TRANSACTION",
                "PRAGMA query_only = 1",
                "SELECT valore FROM effetto_di_prova ORDER BY valore",
                "PRAGMA query_only = 0",
                "END TRANSACTION",
            ),
            sql.istruzioni,
        )
    }

    @Test
    fun `AC-C16 inLettura dentro inTransazione non emette un secondo BEGIN ne alcun pragma`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inTransazione {
            sql.scrivi("uno")
            Esito.Ok(sql.uow.inLettura { sql.uow.inLettura { sql.effetti() } })
        }.atteso()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION"), sql.transazioni())
        assertTrue(sql.istruzioni.none { "query_only" in it }, "nessun pragma: ${sql.istruzioni}")
    }

    @Test
    fun `AC-C19 una scrittura dentro inLettura fallisce con SQLITE_READONLY per query_only`() {
        val sql = DatabaseTracciato(cartella)

        val errore = assertFailsWith<SQLiteException> { sql.uow.inLettura { sql.scrivi("vietato") } }

        assertEquals(SQLiteErrorCode.SQLITE_READONLY, errore.resultCode)
        assertEquals(emptyList(), sql.effetti())
    }

    @Test
    fun `AC-C20 dopo una lettura fallita query_only torna a 0 e la transazione successiva apre BEGIN IMMEDIATE`() {
        val sql = DatabaseTracciato(cartella)

        assertFailsWith<SQLiteException> { sql.uow.inLettura { sql.scrivi("vietato") } }
        val dopoLettura = sql.istruzioni.toList()
        sql.istruzioni.clear()
        sql.uow.inTransazione {
            sql.scrivi("dopo")
            Esito.Ok(Unit)
        }.atteso()

        assertEquals("PRAGMA query_only = 0", dopoLettura[dopoLettura.size - 2], "azzerato prima del ROLLBACK")
        assertEquals("ROLLBACK TRANSACTION", dopoLettura.last())
        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION"), sql.transazioni())
        assertEquals(listOf("dopo"), sql.effetti())
    }

    @Test
    fun `AC-C20 sulla connessione riusata in memoria query_only torna a 0 dopo un eccezione in inLettura`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val uow = UnitaDiLavoroSql(SnastroDatabase(driver))

        val dentro = AtomicReference<Long>()
        assertFailsWith<IllegalArgumentException> {
            uow.inLettura {
                dentro.set(queryOnly(driver))
                throw IllegalArgumentException("guasto")
            }
        }

        assertEquals(1L, dentro.get())
        assertEquals(0L, queryOnly(driver))
    }

    @Test
    fun `AC-C23 dentro una sola inLettura una scrittura confermata da un altro thread non si vede`() {
        val sql = DatabaseTracciato(cartella)
        sql.uow.inTransazione {
            sql.scrivi("prima")
            Esito.Ok(Unit)
        }.atteso()
        val guastoB = AtomicReference<Throwable>()

        val (prima, seconda) = sql.uow.inLettura {
            val prima = sql.effetti()
            val bConfermato = CountDownLatch(1)
            thread(name = "B-scrittore") {
                runCatching {
                    sql.uow.inTransazione {
                        sql.scrivi("di-b")
                        Esito.Ok(Unit)
                    }.atteso()
                }.onFailure(guastoB::set)
                bConfermato.countDown()
            }
            assertTrue(bConfermato.await(ATTESA_S, TimeUnit.SECONDS), "B deve confermare mentre A legge")
            prima to sql.effetti()
        }

        assertNull(guastoB.get(), "B non deve fallire: ${guastoB.get()}")
        assertEquals(listOf("prima"), prima)
        assertEquals(listOf("prima"), seconda, "la seconda SELECT vede la stessa istantanea della prima")
        assertEquals(listOf("di-b", "prima"), sql.uow.inLettura { sql.effetti() }, "una nuova lettura la vede")
    }

    @Test
    fun `AC-C24 inLettura termina senza attendere un BEGIN IMMEDIATE aperto da un altro thread`() {
        val sql = DatabaseTracciato(cartella)
        sql.uow.inTransazione {
            sql.scrivi("confermato")
            Esito.Ok(Unit)
        }.atteso()
        val lockPreso = CountDownLatch(1)
        val rilascia = CountDownLatch(1)
        val scrittore = thread(name = "B-scrittore-lungo") {
            sql.uow.inTransazione {
                sql.scrivi("non-confermato")
                lockPreso.countDown()
                rilascia.await()
                Esito.Ok(Unit)
            }
        }
        try {
            attendiFinche(messaggio = "B tiene il lock di scrittura") { lockPreso.count == 0L }
            val letto = AtomicReference<List<String>>()
            val guasto = AtomicReference<Throwable>()
            thread(name = "A-lettore") {
                runCatching { sql.uow.inLettura { sql.effetti() } }.onSuccess(letto::set).onFailure(guasto::set)
            }

            attendiFinche(1.seconds, "inLettura deve finire ben prima del busy_timeout di 5 s") {
                letto.get() != null || guasto.get() != null
            }

            assertNull(guasto.get(), "nessun SQLITE_BUSY: ${guasto.get()}")
            assertEquals(listOf("confermato"), letto.get())
        } finally {
            rilascia.countDown()
            scrittore.join(ATTESA_S * MILLIS)
        }
    }

    @Test
    fun `AC-C25 la transazione esterna di scrittura apre ancora BEGIN IMMEDIATE`() {
        val sql = DatabaseTracciato(cartella)

        sql.uow.inTransazione {
            sql.scrivi("uno")
            sql.uow.inTransazione {
                sql.scrivi("due")
                Esito.Ok(Unit)
            }
        }.atteso()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION"), sql.transazioni())
    }

    @Test
    fun `AC-C15 inLettura in una transazione ignota allo Stato fallisce subito, senza lasciare DEFERRED`() {
        val sql = DatabaseTracciato(cartella)

        assertFailsWith<IllegalStateException> {
            sql.db.transaction {
                sql.uow.inLettura { sql.effetti() }
            }
        }

        assertEquals(emptyList(), sql.effetti(), "la transazione grezza e annullata: nessun pragma toccato prima")
        sql.istruzioni.clear()
        sql.uow.inTransazione {
            sql.scrivi("dopo")
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "END TRANSACTION"), sql.transazioni(), "niente DEFERRED")
        assertEquals(listOf("dopo"), sql.effetti())
    }

    @Test
    fun `AC-C25 un BEGIN DEFERRED fallito azzera il modo, libera lo slot e poi si apre BEGIN IMMEDIATE`() {
        val guasta = AtomicReference(true)
        val sql = DatabaseTracciato(cartella) {
            if (it.startsWith("BEGIN DEFERRED") && guasta.getAndSet(false)) throw SQLException("guasto iniettato")
        }

        assertFailsWith<SQLException> { sql.uow.inLettura { sql.effetti() } }
        sql.istruzioni.clear()
        sql.uow.inTransazione<Unit> {
            sql.scrivi("da-annullare")
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(listOf("BEGIN IMMEDIATE TRANSACTION", "ROLLBACK TRANSACTION"), sql.transazioni())
        assertEquals(emptyList(), sql.effetti(), "lo slot libero: la transazione e davvero esterna e annulla")
        assertEquals(emptyList(), sql.uow.inLettura { sql.effetti() }, "e la lettura successiva riparte pulita")
    }

    private fun queryOnly(driver: SqlDriver): Long =
        driver.executeQuery(null, "PRAGMA query_only", { cursore ->
            cursore.next()
            QueryResult.Value(checkNotNull(cursore.getLong(0)))
        }, 0).value

    private companion object {
        const val ATTESA_S = 5L
        const val MILLIS = 1_000L
    }
}
