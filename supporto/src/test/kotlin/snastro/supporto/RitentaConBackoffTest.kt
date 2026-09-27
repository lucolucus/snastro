package snastro.supporto

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** AC-C5..AC-C8: [RitentaConBackoff] on virtual time only (runTest + StandardTestDispatcher). */
@OptIn(ExperimentalCoroutinesApi::class)
class RitentaConBackoffTest {
    private val segnalazioni = SegnalazioniRegistrate()

    private fun lavoratore(lavoro: suspend (String) -> Boolean) =
        RitentaConBackoff(lavoro, segnalazioni, attesaIniziale = 1.seconds, attesaMassima = 4.seconds)

    /** A scope on the test's virtual clock that the test can cancel, with a handler recording what escapes. */
    private fun TestScope.scopeCancellabile(sfuggiti: MutableList<Throwable> = mutableListOf()) =
        CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, e -> sfuggiti += e },
        )

    /** `advanceUntilIdle` ignores `backgroundScope`'s work: advance the virtual clock explicitly instead. */
    private fun TestScope.avanzaTutto() {
        advanceTimeBy(1.hours)
        runCurrent()
    }

    @Test
    fun `AC-C5 tre richieste della stessa chiave prima del lavoro producono una sola esecuzione`() = runTest {
        val eseguite = mutableListOf<String>()
        val lavoratore = lavoratore {
            eseguite += it
            true
        }
        lavoratore.avvia(backgroundScope)

        repeat(3) { lavoratore.richiedi("k") }
        avanzaTutto()

        assertEquals(listOf("k"), eseguite)
    }

    @Test
    fun `AC-C5 due chiavi diverse girano una volta ciascuna`() = runTest {
        val eseguite = mutableListOf<String>()
        val lavoratore = lavoratore {
            eseguite += it
            true
        }
        lavoratore.avvia(backgroundScope)

        lavoratore.richiedi("k1")
        lavoratore.richiedi("k2")
        avanzaTutto()

        assertEquals(listOf("k1", "k2"), eseguite)
        assertTrue(segnalazioni.tutte.isEmpty())
    }

    @Test
    fun `AC-C6 cinque fallimenti poi un successo sono separati da 1 2 4 4 4 secondi`() = runTest {
        val istanti = mutableListOf<Long>()
        val lavoratore = lavoratore {
            istanti += testScheduler.currentTime
            istanti.size == 6
        }
        lavoratore.avvia(backgroundScope)

        lavoratore.richiedi("k")
        avanzaTutto()

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L, 4_000L), istanti.zipWithNext { a, b -> b - a })
        val righe = segnalazioni.tutte
        assertEquals(6, righe.size, "one per failure + one on recovery: $righe")
        assertTrue(righe.all { it.causa == null && "'k'" in it.messaggio }, "$righe")
        assertTrue(righe.take(5).none { "riuscito" in it.messaggio })
        assertTrue("riuscito" in righe.last().messaggio)
    }

    @Test
    fun `AC-C6 un successo senza fallimenti precedenti non segnala nulla`() = runTest {
        val lavoratore = lavoratore { true }
        lavoratore.avvia(backgroundScope)

        lavoratore.richiedi("k")
        avanzaTutto()

        assertTrue(segnalazioni.tutte.isEmpty())
    }

    @Test
    fun `AC-C7 una chiave che lancia viene segnalata con l eccezione e ritentata finche lo scope vive`() = runTest {
        val errore = IllegalStateException("guasto")
        var esecuzioni = 0
        val lavoratore = lavoratore {
            esecuzioni++
            throw errore
        }
        val scope = scopeCancellabile()
        lavoratore.avvia(scope)

        lavoratore.richiedi("k")
        advanceTimeBy(31.seconds) // runs at 0, 1, 3, 7, 11, 15, 19, 23, 27, 31 s
        runCurrent()
        assertEquals(10, esecuzioni)
        assertEquals(10, segnalazioni.tutte.size)
        assertTrue(segnalazioni.tutte.all { it.causa === errore && "'k'" in it.messaggio })

        advanceTimeBy(4.seconds) // after 10 consecutive failures k is still scheduled
        runCurrent()
        assertEquals(11, esecuzioni)

        scope.cancel()
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(11, esecuzioni)
        assertEquals(11, segnalazioni.tutte.size)
    }

    @Test
    fun `AC-C7 una nuova richiesta durante l attesa esegue subito e sostituisce il ritento`() = runTest {
        val istanti = mutableListOf<Long>()
        val lavoratore = lavoratore {
            istanti += testScheduler.currentTime
            istanti.size > 1
        }
        lavoratore.avvia(backgroundScope)

        lavoratore.richiedi("k")
        advanceTimeBy(500)
        lavoratore.richiedi("k")
        avanzaTutto()

        assertEquals(listOf(0L, 500L), istanti)
    }

    @Test
    fun `AC-C8 un Error non e un ritento ed esce verso il gestore dello scope`() = runTest {
        val errore = StackOverflowError("finto")
        val sfuggiti = mutableListOf<Throwable>()
        var esecuzioni = 0
        val lavoratore = lavoratore {
            esecuzioni++
            throw errore
        }
        val job = lavoratore.avvia(scopeCancellabile(sfuggiti))

        lavoratore.richiedi("k")
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(errore), sfuggiti)
        assertTrue(segnalazioni.tutte.isEmpty())
        assertTrue(job.isCancelled)
        assertEquals(1, esecuzioni)
    }

    @Test
    fun `AC-C8 la cancellazione del proprio scope ferma il lavoratore senza segnalare`() = runTest {
        val sfuggiti = mutableListOf<Throwable>()
        var esecuzioni = 0
        val scope = scopeCancellabile(sfuggiti)
        val lavoratore = lavoratore {
            esecuzioni++
            scope.cancel() // il lavoratore osserva la cancellazione del PROPRIO scope, non uno straniero
            throw CancellationException("stop")
        }
        val job = lavoratore.avvia(scope)

        lavoratore.richiedi("k")
        advanceUntilIdle()
        lavoratore.richiedi("k")
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertEquals(1, esecuzioni)
        assertTrue(segnalazioni.tutte.isEmpty())
        assertTrue(sfuggiti.isEmpty())
    }

    // --- AC-C91: a FOREIGN CancellationException (the worker's own scope stays active) is a failure --------

    @Test
    fun `AC-C91 un timeout interno a lavoro e un fallimento segnalato e ritentato, non un arresto`() = runTest {
        var tentativi = 0
        val lavoratore = lavoratore {
            tentativi++
            if (tentativi == 1) withTimeout(1.milliseconds) { delay(1.hours) }
            true
        }
        lavoratore.avvia(backgroundScope)

        lavoratore.richiedi("k")
        avanzaTutto()

        assertEquals(2, tentativi, "il primo tentativo scade (timeout straniero), il ritento riesce")
        val righe = segnalazioni.tutte
        assertEquals(2, righe.size, "un fallimento (causa il timeout) + un recupero: $righe")
        assertIs<TimeoutCancellationException>(righe.first().causa)
        assertTrue("riuscito" in righe.last().messaggio)

        lavoratore.richiedi("k2")
        avanzaTutto()

        assertEquals(3, tentativi, "il lavoratore e ancora vivo: una chiave richiesta dopo gira")
    }

    @Test
    fun `AC-C91 attendere un Deferred gia cancellato e un fallimento segnalato e ritentato, non un arresto`() =
        runTest {
            var tentativi = 0
            val lavoratore = lavoratore {
                tentativi++
                if (tentativi == 1) {
                    val differito = CompletableDeferred<Unit>()
                    differito.cancel()
                    differito.await() // CancellationException straniera: non e' il lavoratore a cancellarsi
                }
                true
            }
            lavoratore.avvia(backgroundScope)

            lavoratore.richiedi("k")
            avanzaTutto()

            assertEquals(2, tentativi, "il primo tentativo fallisce (Deferred straniero), il ritento riesce")
            val righe = segnalazioni.tutte
            assertEquals(2, righe.size, "un fallimento (causa il Deferred cancellato) + un recupero: $righe")
            assertIs<CancellationException>(righe.first().causa)
            assertTrue("riuscito" in righe.last().messaggio)

            lavoratore.richiedi("k2")
            avanzaTutto()

            assertEquals(3, tentativi, "il lavoratore e ancora vivo: una chiave richiesta dopo gira")
        }

    @Test
    fun `AC-C8 il sorgente non usa runCatching`() {
        val sorgente = File("src/main/kotlin/snastro/supporto/RitentaConBackoff.kt")
        assertTrue(sorgente.isFile, "run from the module directory: ${sorgente.absolutePath}")
        assertFalse("runCatching" in sorgente.readText())
    }

    @Test
    fun `AC-C6 un attesa massima minore dell iniziale e rifiutata`() {
        assertFailsWith<IllegalArgumentException> {
            RitentaConBackoff<String>({ true }, segnalazioni, attesaIniziale = 4.seconds, attesaMassima = 1.seconds)
        }
    }
}
