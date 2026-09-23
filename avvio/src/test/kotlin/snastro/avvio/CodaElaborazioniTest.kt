package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CodaElaborazioni] green-on-its-own: `EseguiProssimaElaborazioneServizio` and
 * `RecuperaElaborazioniInterrotteServizio` (`:trascrizione:applicazione`) are never imported here —
 * only the plain function shapes [CodaElaborazioni] actually consumes, per this block's boundary.
 * Time-based scenarios (AC-233/235/312/313) run on
 * [StandardTestDispatcher] with `advanceTimeBy`, never a real sleep; the concurrency guarantee
 * (AC-314) runs on the REAL dedicated single-thread dispatcher with real threads, proven with
 * latches (also never a sleep).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodaElaborazioniTest {
    @Test
    fun `AC-233 all avvio il recupero gira prima di ogni tentativo di avanzamento`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ordine = mutableListOf<String>()
        CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                ordine += "esegui"
                Esito.Ok(Unit)
            },
            recuperaElaborazioniInterrotte = {
                ordine += "recupera"
                Esito.Ok(Unit)
            },
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        runCurrent() // basta far girare cio' che e' gia' in coda: nessun advance di tempo necessario
        assertEquals("recupera", ordine.first(), "il recupero precede qualunque tentativo di avanzamento")
        assertTrue(ordine.contains("esegui"))
        scope.cancel()
    }

    @Test
    fun `AC-234 con due in attesa la seconda parte da sola dopo che la prima e terminale`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val rimanenti = ArrayDeque(listOf("uno", "due"))
        val completate = mutableListOf<String>()
        CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                rimanenti.removeFirstOrNull()?.let { completate += it }
                Esito.Ok(Unit)
            },
            recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        // Un solo avanzamento (quello automatico all'avvio) drena da sola tutta la coda gia'
        // in_attesa: nessun secondo trigger esterno serve perche' la seconda parta.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("uno", "due"), completate, "la seconda parte solo dopo che la prima e' conclusa")
        scope.cancel()
    }

    @Test
    fun `AC-235 con i modelli mancanti le Elaborazioni restano in attesa finche non diventano pronti`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        var pronti = false
        val chiamate = AtomicInteger(0)
        CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                chiamate.incrementAndGet()
                Esito.Ok(Unit)
            },
            recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
            modelliPronti = { pronti },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, chiamate.get(), "nessun tentativo finche' i modelli non sono pronti")

        pronti = true
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(chiamate.get() > 0, "la coda riparte da sola quando i modelli diventano pronti")
        scope.cancel()
    }

    @Test
    fun `AC-312 un escape da esegui fa girare il recupero prima del prossimo elemento e non ferma il worker`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(dispatcher)
            val primaVolta = AtomicBoolean(true)
            val recuperi = AtomicInteger(0)
            val eseguite = AtomicInteger(0)
            val coda = CodaElaborazioni(
                scope = scope,
                eseguiProssimaElaborazione = {
                    eseguite.incrementAndGet()
                    if (primaVolta.compareAndSet(true, false)) throw OutOfMemoryError("di prova")
                    Esito.Ok(Unit)
                },
                recuperaElaborazioniInterrotte = {
                    recuperi.incrementAndGet()
                    Esito.Ok(Unit)
                },
                modelliPronti = { true },
                dispatcherSingoloThread = dispatcher,
            )
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(2, recuperi.get(), "il recupero dell'avvio (AC-233) piu' quello dopo l'escape (AC-312)")
            assertTrue(eseguite.get() >= 2, "il worker riprende a chiamare esegui dopo l'escape, non muore")
            assertTrue(coda.lavoro.isActive, "il worker resta vivo")
            scope.cancel()
        }

    @Test
    fun `AC-312 una CancellationException un InterruptedException e un errore inatteso non fermano il worker`() =
        runTest {
            for (fallimentoFabbrica in listOf<() -> Throwable>(
                { CancellationException("di prova") },
                { InterruptedException("di prova") },
                { IllegalStateException("inattesa") },
            )) {
                val dispatcher = StandardTestDispatcher(testScheduler)
                val scope = CoroutineScope(dispatcher)
                val primaVolta = AtomicBoolean(true)
                val eseguite = AtomicInteger(0)
                val coda = CodaElaborazioni(
                    scope = scope,
                    eseguiProssimaElaborazione = {
                        eseguite.incrementAndGet()
                        if (primaVolta.compareAndSet(true, false)) throw fallimentoFabbrica()
                        Esito.Ok(Unit)
                    },
                    recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
                    modelliPronti = { true },
                    dispatcherSingoloThread = dispatcher,
                )
                advanceTimeBy(3_000)
                runCurrent()
                val nome = fallimentoFabbrica()::class.simpleName
                assertTrue(eseguite.get() >= 2, "il worker riprende dopo $nome")
                assertTrue(coda.lavoro.isActive)
                scope.cancel()
                // Il caso InterruptedException fa ripristinare il flag di interruzione sul thread
                // chiamante (stesso comportamento di eseguiFase/DispatcherEventiInMemoria): lo si
                // consuma qui perche' non trapeli nell'iterazione successiva o nel resto del test.
                Thread.interrupted()
            }
        }

    @Test
    fun `AC-313 un avvio sempre rifiutato non gira a vuoto e si blocca per la sessione`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val chiamate = AtomicInteger(0)
        val bloccata = AtomicInteger(0)
        val coda = CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                chiamate.incrementAndGet()
                Esito.Errore(ErroreDiProva.Fallito("un abbonato sincrono rifiuta sempre"))
            },
            recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
            modelliPronti = { true },
            segnalaCodaBloccata = { bloccata.incrementAndGet() },
            dispatcherSingoloThread = dispatcher,
        )
        advanceUntilIdle() // dopo il blocco il worker non riprogramma piu' nulla: termina di avanzare
        val chiamateAlBlocco = chiamate.get()
        assertEquals(3, chiamateAlBlocco, "tentativi limitati (non gira a vuoto)")
        assertEquals(1, bloccata.get(), "segnalato esattamente una volta")

        coda.avanza() // un ulteriore trigger, esterno, dopo il blocco
        advanceUntilIdle()
        assertEquals(chiamateAlBlocco, chiamate.get(), "nessun ulteriore tentativo: bloccata per la sessione")
        scope.cancel()
    }

    @Test
    fun `AC-313 i tentativi sono distanziati da un back off crescente, mai a distanza zero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val istantiChiamata = mutableListOf<Long>()
        CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                istantiChiamata += testScheduler.currentTime
                Esito.Errore(ErroreDiProva.Fallito("rifiutato"))
            },
            recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        advanceUntilIdle()
        assertEquals(3, istantiChiamata.size)
        val distanze = istantiChiamata.zipWithNext { a, b -> b - a }
        assertTrue(distanze.all { it > 0 }, "mai un tentativo a distanza zero")
        assertTrue(distanze[1] > distanze[0], "il back-off cresce: ${distanze[0]} poi ${distanze[1]}")
        scope.cancel()
    }

    @Test
    fun `AC-314 due richieste concorrenti di avanzamento non eseguono mai due Elaborazioni insieme`() {
        val dentro = CountDownLatch(1)
        val procedi = CountDownLatch(1)
        val primaCompletata = CountDownLatch(1)
        val primaVolta = AtomicBoolean(true)
        val concorrenti = AtomicInteger(0)
        val massimoConcorrenti = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val coda = CodaElaborazioni(
            scope = scope,
            eseguiProssimaElaborazione = {
                val n = concorrenti.incrementAndGet()
                massimoConcorrenti.updateAndGet { max(it, n) }
                if (primaVolta.compareAndSet(true, false)) {
                    dentro.countDown()
                    assertTrue(procedi.await(10, TimeUnit.SECONDS), "il test avrebbe dovuto sbloccare in tempo")
                }
                concorrenti.decrementAndGet()
                primaCompletata.countDown()
                Esito.Ok(Unit)
            },
            recuperaElaborazioniInterrotte = { Esito.Ok(Unit) },
            modelliPronti = { true },
        )
        coda.avanza()
        assertTrue(dentro.await(10, TimeUnit.SECONDS), "la prima esecuzione avrebbe dovuto partire")

        val via = CountDownLatch(1)
        val fili = List(8) {
            Thread {
                via.await()
                repeat(20) { coda.avanza() }
            }
        }
        fili.forEach { it.start() }
        via.countDown()
        fili.forEach { it.join(10_000) }
        assertTrue(fili.none(Thread::isAlive), "tutti i thread di stimolo devono terminare in tempo")

        assertEquals(1, massimoConcorrenti.get(), "mai piu' di un'esecuzione concorrente (AC-314)")

        procedi.countDown()
        assertTrue(primaCompletata.await(10, TimeUnit.SECONDS))
        scope.cancel()
    }
}
