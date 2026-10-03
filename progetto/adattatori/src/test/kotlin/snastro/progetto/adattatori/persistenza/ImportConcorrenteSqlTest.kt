package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.AggiungiRegistrazioneServizio
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.restaVeroPer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * INV-I2 / D-0059 under two real concurrent imports on a FILE-backed project database: `AggiungiRegistrazioneServizio`
 * reads the Progetto's Registrazioni and then mints its `aggiuntaAlle` from them inside one transaction. That
 * read-then-mint is race-free only because every write transaction begins `BEGIN IMMEDIATE` (`DriverSqliteImmediato`):
 * the second import waits at its BEGIN until the first commits, then reads the first's rows.
 *
 * Deterministic hand-off: the first import to read holds its transaction open until the other import has ENTERED
 * its own `inTransazione` (observed through a recording `UnitaDiLavoro`, no guessed delay), then asserts that the other
 * still has not read for [FINESTRA_MS] more, and finally commits. The log must show the second read after the first
 * commit. Were the transactions DEFERRED, the second would read inside that window (the hold fails the first import and
 * the log order breaks) — checked by switching the driver to DEFERRED. The window is counted from the second import's
 * entry, not from the first read, so a loaded host cannot shorten it; under `BEGIN IMMEDIATE` it costs [FINESTRA_MS].
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
            val log = ConcurrentLinkedQueue<String>()
            val entrati = ConcurrentHashMap.newKeySet<String>()
            val letture = AtomicInteger()
            val primoLettore = AtomicBoolean(true)
            val unitaDiLavoro = object : UnitaDiLavoro {
                override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
                    entrati.add(Thread.currentThread().name)
                    return eventi.unitaDiLavoro.inTransazione(blocco).also {
                        log.add("commit ${Thread.currentThread().name}")
                    }
                }
            }
            val registrazioni = object : RegistrazioneRepository by registrazioniSql {
                override fun delProgetto(id: ProgettoId) = registrazioniSql.delProgetto(id).also {
                    log.add("lettura ${Thread.currentThread().name}")
                    letture.incrementAndGet()
                    if (primoLettore.compareAndSet(true, false)) {
                        attendiFinche(ATTESA_THREAD_MS.milliseconds, "il secondo import non è entrato") {
                            entrati.size == 2
                        }
                        restaVeroPer(FINESTRA_MS.milliseconds, "il secondo ha letto a primo aperto") {
                            letture.get() == 1
                        }
                    }
                }
            }
            val clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC) // same ms for both
            val file = listOf("a.m4a", "b.m4a")
            val sonda = SondaAudioFinta(leggibili = file.associateWith { InfoAudio(1_000, LocalDate.of(2026, 3, 12)) })
            fun servizio(prefisso: String) = AggiungiRegistrazioneServizio(
                unitaDiLavoro,
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
                checkNotNull(esito.get()) { "${t.name} non è terminato entro ${ATTESA_THREAD_MS} ms" }
            }

            esiti.forEach { assertIs<Esito.Ok<Unit>>(it.getOrThrow()) }
            val ordine = log.toList()
            val commitPrimo = ordine.indexOf(ordine.first { it.startsWith("lettura") }.replace("lettura", "commit"))
            assertTrue(
                commitPrimo < ordine.indexOfLast { it.startsWith("lettura") },
                "il secondo import ha letto prima del commit del primo: $ordine",
            )
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
        const val FINESTRA_MS = 200L
        const val ATTESA_THREAD_MS = 15_000L
    }
}
