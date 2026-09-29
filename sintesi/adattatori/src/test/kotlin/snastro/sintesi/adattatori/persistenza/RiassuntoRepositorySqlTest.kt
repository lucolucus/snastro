package snastro.sintesi.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteException
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.databaseInMemoria
import snastro.sintesi.applicazione.porte.PredisposizioneSintesi
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryContratto
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.supporto.test.attendiFinche
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * D2 (dev-architecture-app.md#porta-contratto): [RiassuntoRepositoryContratto] against
 * [RiassuntoRepositorySql] on a fresh in-memory [SnastroDatabase] (AC-S111). [predisponi] seeds the
 * contract's parent rows with raw SQL (never `progettoQueries`/`registrazioneQueries` — ADR 0021
 * clause 2). AC-S112's index -> `Errore` half is already the inherited `AC-S66` tests; only the
 * "any OTHER constraint fault is rethrown raw" half (ADR 0003) needs its own test below, since the
 * Finta has no concept of a real foreign key.
 */
class RiassuntoRepositorySqlTest : RiassuntoRepositoryContratto() {
    private lateinit var driver: SqlDriver

    override fun repository(): RiassuntoRepository {
        val config = SQLiteConfig().apply { enforceForeignKeys(true) }
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        SnastroDatabase.Schema.create(driver)
        val db = SnastroDatabase(driver)
        return RiassuntoRepositorySql(db, UnitaDiLavoroSql(db))
    }

    override fun predisponi(predisposizione: PredisposizioneSintesi) {
        semina(driver, predisposizione)
    }

    @Test
    fun `AC-S112 un vincolo diverso dagli indici parziali non e mappato e arriva grezzo`() {
        // No registrazione seeded: the immediate FK riassunto.registrazione_id -> registrazione(id) refuses it,
        // a constraint the Finta (RiassuntoRepositoryFinta) has no concept of.
        val db = databaseInMemoria()
        val repo = RiassuntoRepositorySql(db, UnitaDiLavoroSql(db))

        assertFailsWith<SQLiteException> {
            repo.salva(unRiassunto("riassunto-1", RiassuntoRepositoryContratto.REGISTRAZIONE))
        }
    }

    /**
     * ADR 0029 §5, AC-C30/C31: [RiassuntoRepositorySql.trova] reads the root and its children from ONE snapshot.
     * A test driver parks the reader right after the root `riassunto` SELECT; a rewrite (delete + re-insert, a
     * different number of `decisioni`) committed by another thread while it is parked must never surface: the
     * parked read returns the OLD Riassunto whole, a fresh read after release returns the NEW one. Latch-driven
     * ([attendiFinche], ADR 0028 §3); a throwaway probe removing the `inLettura` wrap (never committed) makes this
     * fail with a mix of the OLD root and the NEW children (or the reverse).
     */
    @Test
    fun `AC-C31 trova legge la radice e i figli in una sola istantanea con una riscrittura parcheggiata in mezzo`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            semina(driverReale, PredisposizioneSintesi(setOf(PROGETTO), mapOf(REGISTRAZIONE to PROGETTO)))
            val uowScrittore = UnitaDiLavoroSql(scrittore)
            val repoScrittore = RiassuntoRepositorySql(scrittore, uowScrittore)
            val vecchio = unRiassunto(ID.valore, REGISTRAZIONE).conAvvio().conCompletamento(bozza(1), STRUTTURA)
            repoScrittore.salva(vecchio).atteso()

            val parcheggiato = CountDownLatch(1)
            val via = CountDownLatch(1)
            val db = SnastroDatabase(DriverParcheggiato(driverReale, parcheggiato, via))
            val lettore = RiassuntoRepositorySql(db, UnitaDiLavoroSql(db))

            val letto = AtomicReference<Riassunto?>()
            val lettura = thread(name = "lettore") { letto.set(lettore.trova(ID)) }

            attendiFinche(messaggio = "il lettore deve parcheggiarsi dopo il SELECT su riassunto") {
                parcheggiato.count == 0L
            }
            riscrivi(uowScrittore, repoScrittore)
            via.countDown()
            lettura.join(ATTESA_FINE_MS)

            val trovato = assertNotNull(letto.get())
            assertEquals(1, trovato.decisioni.size, "la lettura precede la riscrittura per intero")
            val dopo = assertNotNull(repoScrittore.trova(ID))
            assertEquals(2, dopo.decisioni.size, "una nuova lettura vede la riscrittura")
        } finally {
            reale.chiudi()
        }
    }

    /**
     * A83: [salva]'s existing-row branch must not silently accept a 0-row UPDATE. Here the stored row is still
     * `in_attesa` (never advanced through `avvia`), while the incoming, in-memory [Riassunto] jumped straight to
     * `pronto` (a save more than one transition ahead) — `eseguiConcludi`'s `WHERE stato = 'in_corso'` cannot
     * match, so without the `check` in `aggiornaRadiceEsistente` [salva] would return `Ok(Unit)` and still write
     * `pronto` children against a row that never actually became `pronto` (every later read then throws, INV-S1).
     */
    @Test
    fun `A83 salva su in_attesa con un Riassunto pronto in memoria fallisce forte senza scrivere figli`() {
        val db = SnastroDatabase(driver)
        val repo = RiassuntoRepositorySql(db, UnitaDiLavoroSql(db))
        val id = RiassuntoId("riassunto-a83")
        repo.salva(unRiassunto(id.valore, RiassuntoRepositoryContratto.REGISTRAZIONE)).atteso()

        val pronto = unRiassunto(id.valore, RiassuntoRepositoryContratto.REGISTRAZIONE)
            .conAvvio()
            .conCompletamento(bozza(1), STRUTTURA)

        assertFailsWith<IllegalStateException> { repo.salva(pronto) }

        val letto = checkNotNull(repo.trova(id))
        assertTrue(letto.inAttesa, "la riga non deve avanzare quando salva fallisce forte")
        assertEquals(emptyList(), letto.decisioni, "nessun figlio orfano scritto contro una radice non pronto")
    }

    /** Delete + re-insert of [ID] with a different number of `decisioni`, in ONE write transaction. */
    private fun riscrivi(uow: UnitaDiLavoroSql, repo: RiassuntoRepositorySql) {
        uow.inTransazione {
            repo.rimuovi(ID).atteso()
            repo.salva(unRiassunto(ID.valore, REGISTRAZIONE).conAvvio().conCompletamento(bozza(2), STRUTTURA))
        }.atteso()
    }

    /** [DatabaseProgetto] keeps its production driver private (the app never needs it): tests only. */
    private fun driverDi(database: DatabaseProgetto): SqlDriver {
        val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
        return campo.get(database) as SqlDriver
    }

    private fun bozza(nDecisioni: Int): BozzaRiassunto = BozzaRiassunto(
        sommario = null,
        decisioni = (1..nDecisioni).map { BozzaElemento("decisione $it.", listOf(1), null) },
        questioniAperte = emptyList(),
        azioni = emptyList(),
        puntiChiave = emptyList(),
    )

    /**
     * Blocks the FIRST `executeQuery` on `riassunto` (the root row already read; the trailing space keeps
     * `riassunto_elemento`/`riassunto_fonte` out): counts down [parcheggiato], then waits (bounded) on [via].
     * The writer's own statements, issued through a SEPARATE, unwrapped [SnastroDatabase] on [delegato], pass
     * straight through untouched.
     */
    private class DriverParcheggiato(
        private val delegato: SqlDriver,
        private val parcheggiato: CountDownLatch,
        private val via: CountDownLatch,
    ) : SqlDriver by delegato {
        private val scattato = AtomicBoolean(false)

        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            val risultato = delegato.executeQuery(identifier, sql, mapper, parameters, binders)
            if ("FROM riassunto " in sql && scattato.compareAndSet(false, true)) {
                parcheggiato.countDown()
                via.await(ATTESA_SCRITTORE_MS, TimeUnit.MILLISECONDS)
            }
            return risultato
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-c31")
        val REGISTRAZIONE = RegistrazioneId("registrazione-c31")
        val ID = RiassuntoId("riassunto-c31")
        val STRUTTURA = unaStruttura(1 to 1)
        const val ATTESA_SCRITTORE_MS = 5_000L
        const val ATTESA_FINE_MS = 10_000L
    }
}
