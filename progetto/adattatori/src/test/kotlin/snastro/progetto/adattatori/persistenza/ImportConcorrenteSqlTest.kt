package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.SondaAudioFinta
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * INV-I2 / D-0059 under two real concurrent imports on a FILE-backed project database: `AggiungiRegistrazioneServizio`
 * reads the Progetto's Registrazioni and then mints its `aggiuntaAlle` from them inside one transaction. That
 * read-then-mint is race-free only because every write transaction begins `BEGIN IMMEDIATE` (`DriverSqliteImmediato`):
 * the second import waits at its BEGIN until the first commits, then reads the first's rows.
 *
 * The test widens the window on purpose: after its read, each import waits (bounded) for the other to have read too.
 * Under `BEGIN IMMEDIATE` the other never can, so the first proceeds after the bound and the second reads its rows.
 * Were the transactions DEFERRED, both would read the same snapshot and mint the same instants, and the second writer
 * would fail with `SQLITE_BUSY` — this test then fails (checked by switching the driver to DEFERRED).
 */
class ImportConcorrenteSqlTest {
    @Test
    fun `INV-I2 due import concorrenti nello stesso Progetto non intrecciano gli istanti di aggiunta`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database
            db.progettoQueries.inserisci(PROGETTO.valore, "Progetto di prova")
            val eventi = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
            val registrazioniSql = RegistrazioneRepositorySql(db)
            val entrambeLette = CountDownLatch(2)
            val registrazioni = object : RegistrazioneRepository by registrazioniSql {
                override fun delProgetto(id: ProgettoId) = registrazioniSql.delProgetto(id).also {
                    entrambeLette.countDown()
                    entrambeLette.await(FINESTRA_MS, TimeUnit.MILLISECONDS)
                }
            }
            val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC) // same ms for both
            val file = listOf("a.m4a", "b.m4a")
            val sonda = SondaAudioFinta(leggibili = file.associateWith { InfoAudio(1_000, LocalDate.of(2026, 3, 12)) })
            fun servizio(prefisso: String) = AggiungiRegistrazioneServizio(
                eventi.unitaDiLavoro,
                generatoreCon(prefisso),
                clock,
                ProgettoRepositorySql(db),
                registrazioni,
                IncontroRepositorySql(db),
                sonda,
                ArchivioAudioFinta().apply { file.forEach { conSorgente(it) } },
                eventi,
            )

            val partenza = CyclicBarrier(2)
            val esiti = listOf("x", "y").map { prefisso ->
                val esito = AtomicReference<Result<Esito<Unit>>>()
                val servizio = servizio(prefisso)
                esito to thread(name = "import-$prefisso") {
                    partenza.await()
                    val comando = AggiungiRegistrazione(PROGETTO, file, Destinazione.NuovoIncontro)
                    esito.set(runCatching { servizio.esegui(comando) })
                }
            }.map { (esito, t) ->
                t.join(ATTESA_THREAD_MS)
                esito.get()
            }

            esiti.forEach { assertIs<Esito.Ok<Unit>>(it.getOrThrow()) }
            val perImport = registrazioniSql.delProgetto(PROGETTO)
                .groupBy({ it.id.valore.substringBefore('-') }, { it.aggiuntaAlle })
            assertEquals(setOf("x", "y"), perImport.keys)
            val (primo, secondo) = perImport.values.sortedBy { it.min() }
            assertTrue(primo.max() < secondo.min(), "gli istanti dei due import si intrecciano: $perImport")
        } finally {
            database.chiudi()
        }
    }

    private fun generatoreCon(prefisso: String) = object : GeneratoreId {
        private val n = AtomicInteger()
        override fun nuovo() = "$prefisso-${n.incrementAndGet()}"
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        const val FINESTRA_MS = 500L
        const val ATTESA_THREAD_MS = 15_000L
    }
}
