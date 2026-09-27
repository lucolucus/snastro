package snastro.trascrizione.adattatori.persistenza

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unTrascritto
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0029 §5, AC-C28/C31/C36: [TrascrittoRepositorySql.trova] reads `trascritto` (the counters) and `segmento`
 * from ONE snapshot ([snastro.kernel.LetturaCoerente.inLettura]), never a raw `db.transactionWithResult` (CR-3b).
 * A test driver PARKS the reader right after the root `trascritto` SELECT (the counters row already read); a
 * Revisione committed by another thread WHILE it is parked must never surface: the parked read returns the OLD
 * Trascritto in full — counters AND Segmenti/Voci, never a mix — and a fresh read after release returns the NEW
 * one. Latch-driven throughout ([attendiFinche], no `Thread.sleep`, no fixed wait — dev-architecture-app.md#test,
 * ADR 0028 §3); a throwaway probe removing the `inLettura` wrap (never committed) makes this fail with a mixed
 * OLD-counters/NEW-Segmenti Trascritto instead.
 */
class TrascrittoRepositorySqlLetturaAtomicaTest {
    @Test
    fun `AC-C31 trova legge contatori e Segmenti in una sola istantanea con una Revisione parcheggiata in mezzo`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            predisponi(scrittore)
            val uowScrittore = UnitaDiLavoroSql(scrittore)
            TrascrittoRepositorySql(scrittore, uowScrittore)
                .salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))

            val parcheggiato = CountDownLatch(1)
            val via = CountDownLatch(1)
            val db = SnastroDatabase(DriverParcheggiato(driverReale, parcheggiato, via))
            val lettore = TrascrittoRepositorySql(db, UnitaDiLavoroSql(db))

            val letto = AtomicReference<Trascritto?>()
            val lettura = thread(name = "lettore") { letto.set(lettore.trova(R)) }

            attendiFinche(messaggio = "il lettore deve parcheggiarsi dopo il SELECT su trascritto") {
                parcheggiato.count == 0L
            }
            dividi(scrittore, uowScrittore)
            via.countDown()
            lettura.join(ATTESA_FINE_MS)

            val trascritto = assertNotNull(letto.get())
            assertEquals(3, trascritto.prossimaVoce, "la lettura precede la Revisione per intero")
            assertEquals(2, trascritto.voci.size, "...e le sue Voci/Segmenti, mai un misto")
            val dopo = assertNotNull(TrascrittoRepositorySql(scrittore, uowScrittore).trova(R))
            assertEquals(4, dopo.prossimaVoce, "la Revisione e committata comunque dopo la lettura")
            assertEquals(3, dopo.voci.size, "una nuova lettura vede la Voce in piu della Revisione")
        } finally {
            reale.chiudi()
        }
    }

    /**
     * ADR 0029 §5, AC-C28: called OUTSIDE any unit of work, [TrascrittoRepositorySql.trova] opens the outermost
     * `BEGIN DEFERRED` read (rule 1) — it never queues behind a writer's uncommitted `BEGIN IMMEDIATE`, unlike the
     * raw `db.transactionWithResult` it replaced (D-0008/CR-3b). The writer takes the write lock BEFORE the reader
     * starts (latch-driven, no sleep): the OLD `TrascrittoRepositorySql.trova` (a plain `transactionWithResult`,
     * itself `BEGIN IMMEDIATE`) would queue behind it up to `busy_timeout` (5 s) or throw `SQLITE_BUSY`; this
     * discriminates because [TrascrittoRepositorySqlLetturaAtomicaTest]'s own AC-C31 case (reader parked FIRST,
     * writer committing after) passes even with that old, IMMEDIATE `trova` — a throwaway revert to a raw
     * `db.transactionWithResult` in [TrascrittoRepositorySql.trova] makes this case fail (queues past 1 s / BUSY),
     * confirmed manually and never committed.
     */
    @Test
    fun `AC-C28 trova non attende uno scrittore con BEGIN IMMEDIATE non committato preso prima`(
        @TempDir cartella: File,
    ) {
        val reale = apriDatabaseProgetto(cartella)
        try {
            val driverReale = driverDi(reale)
            val scrittore = SnastroDatabase(driverReale)
            predisponi(scrittore)
            val uowScrittore = UnitaDiLavoroSql(scrittore)
            TrascrittoRepositorySql(scrittore, uowScrittore)
                .salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = R))

            val lockPreso = CountDownLatch(1)
            val rilascia = CountDownLatch(1)
            val scrittoreThread = thread(name = "scrittore-immediate-non-committato") {
                uowScrittore.inTransazione {
                    scrittore.trascrittoQueries.aggiornaContatori(
                        prossimaVoce = 99L,
                        prossimoSegmento = 99L,
                        registrazioneId = R.valore,
                    )
                    lockPreso.countDown()
                    rilascia.await()
                    Esito.Ok(Unit)
                }
            }
            try {
                attendiFinche(messaggio = "lo scrittore deve tenere il BEGIN IMMEDIATE non committato") {
                    lockPreso.count == 0L
                }

                val db = SnastroDatabase(driverReale)
                val lettore = TrascrittoRepositorySql(db, UnitaDiLavoroSql(db))
                val letto = AtomicReference<Trascritto?>()
                val guasto = AtomicReference<Throwable>()
                val lettura = thread(name = "lettore-deferred") {
                    runCatching { lettore.trova(R) }.onSuccess(letto::set).onFailure(guasto::set)
                }

                attendiFinche(1.seconds, messaggio = "trova deve tornare ben prima del busy_timeout di 5 s") {
                    letto.get() != null || guasto.get() != null
                }
                lettura.join(ATTESA_FINE_MS)

                assertNull(guasto.get(), "nessun SQLITE_BUSY / attesa dello scrittore: ${guasto.get()}")
                val trovato = assertNotNull(letto.get())
                assertEquals(3, trovato.prossimaVoce, "l'ultimo COMMITTATO, mai la scrittura in corso non confermata")
            } finally {
                rilascia.countDown()
                scrittoreThread.join(ATTESA_FINE_MS)
            }
        } finally {
            reale.chiudi()
        }
    }

    private fun dividi(db: SnastroDatabase, uow: UnitaDiLavoroSql) {
        val repo = TrascrittoRepositorySql(db, uow)
        uow.inTransazione {
            val t = checkNotNull(repo.trova(R))
            t.dividi(VoceId(1), setOf(SegmentoId(3))).atteso() // Segmento 3 -> new Voce 3, prossimaVoce 3 -> 4
            repo.salva(t)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun predisponi(db: SnastroDatabase) {
        db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
        db.registrazioneQueries.inserisci(
            id = R.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione di prova",
            riferimentoAudio = "audio/${R.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-27",
            aggiuntaAlle = 0L,
        )
    }

    /** [DatabaseProgetto] keeps its production driver private (the app never needs it): tests only. */
    private fun driverDi(database: DatabaseProgetto): SqlDriver {
        val campo = DatabaseProgetto::class.java.getDeclaredField("driver").apply { isAccessible = true }
        return campo.get(database) as SqlDriver
    }

    /**
     * Blocks the FIRST `executeQuery` on `trascritto` (the root row already read): counts down [parcheggiato],
     * then waits (bounded) on [via]. Every other statement — in particular the writer's own, issued through a
     * SEPARATE, unwrapped [SnastroDatabase] on [delegato] — passes straight through untouched.
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
            if ("FROM trascritto" in sql && scattato.compareAndSet(false, true)) {
                parcheggiato.countDown()
                via.await(ATTESA_SCRITTORE_MS, TimeUnit.MILLISECONDS)
            }
            return risultato
        }
    }

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        const val ATTESA_SCRITTORE_MS = 5_000L
        const val ATTESA_FINE_MS = 10_000L
    }
}
