package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CodaElaborazioni] green-on-its-own: its collaborators ([FonteAvanzamento] plus two more plain
 * functions) are all over primitive ids (`avvio/src/main` never imports `snastro.trascrizione`,
 * `GrafoR0Test`'s AC-350 guard), so every scenario here is driven by a small scripted lambda — no
 * fake port/repository needed. Time-based scenarios run on [StandardTestDispatcher] with
 * `advanceTimeBy`, never a real sleep; the concurrency guarantee (AC-314) and the stop protocol
 * (rework items 3/4) run on the REAL dedicated single-thread dispatcher with real threads, proven
 * with latches — also never a sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodaElaborazioniTest {
    @Test
    fun `AC-233 il recupero gira prima di ogni tentativo di avanzamento`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ordine = mutableListOf<String>()

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = {
                    ordine += "esegui"
                    RisultatoTentativo.Nessuno
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = { ordine += "recupera" },
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        runCurrent()

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
            fonte = FonteAvanzamento(
                prossima = { esclusi ->
                    val id = rimanenti.firstOrNull { it !in esclusi }
                    if (id == null) {
                        RisultatoTentativo.Nessuno
                    } else {
                        rimanenti.remove(id)
                        completate += id
                        RisultatoTentativo.Avviata(id)
                    }
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("uno", "due"), completate, "la seconda parte solo dopo che la prima e' conclusa")
        scope.cancel()
    }

    @Test
    fun `AC-235 con i modelli mancanti la coda resta in attesa finche non diventano pronti`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        var pronti = false
        val chiamate = AtomicInteger(0)

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = {
                    chiamate.incrementAndGet()
                    RisultatoTentativo.Nessuno
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
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
    fun `AC-312 un escape non ferma il worker che ritenta e riesce`() = runTest {
        for (guasto in listOf<Throwable>(OutOfMemoryError("di prova"), InterruptedException("di prova"))) {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(dispatcher)
            val primaVolta = AtomicBoolean(true)
            val completate = mutableListOf<String>()
            val recuperi = AtomicInteger(0)

            CodaElaborazioni(
                scope = scope,
                fonte = FonteAvanzamento(
                    prossima = {
                        if (primaVolta.compareAndSet(true, false)) throw guasto
                        if (completate.isEmpty()) {
                            completate += "id-1"
                            RisultatoTentativo.Avviata("id-1")
                        } else {
                            RisultatoTentativo.Nessuno
                        }
                    },
                    ultimaTentata = { "id-1" },
                ),
                recuperaElaborazioniInterrotte = { recuperi.incrementAndGet() },
                modelliPronti = { true },
                dispatcherSingoloThread = dispatcher,
            )
            advanceTimeBy(3_000)
            runCurrent()

            assertEquals(listOf("id-1"), completate, "il ritentativo dopo l'escape riesce")
            assertEquals(2, recuperi.get(), "il recupero dell'avvio (AC-233) piu' quello dopo l'escape (AC-312)")
            scope.cancel()
            Thread.interrupted() // pulisce il flag per l'iterazione/test successivo
        }
    }

    @Test
    fun `AC-313 un avvio sempre rifiutato viene escluso dopo 3 tentativi, la seconda parte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val tentativi = AtomicInteger(0)
        val completate = mutableListOf<String>()
        val bloccate = mutableListOf<String>()

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = { esclusi ->
                    when {
                        "vecchia" !in esclusi -> {
                            tentativi.incrementAndGet()
                            RisultatoTentativo.Rifiutata("vecchia")
                        }
                        completate.isEmpty() -> {
                            completate += "recente"
                            RisultatoTentativo.Avviata("recente")
                        }
                        else -> RisultatoTentativo.Nessuno
                    }
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
            segnalaElaborazioneBloccata = { bloccate += it },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, tentativi.get(), "tentativi limitati, non gira a vuoto")
        assertEquals(listOf("vecchia"), bloccate, "segnalato esattamente una volta")
        assertEquals(listOf("recente"), completate, "gli altri elementi proseguono")
        scope.cancel()
    }

    @Test
    fun `AC-313 rework item 2 - gli escape contano come i rifiuti ed escludono dopo 3 tentativi`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val tentativi = AtomicInteger(0)
        val completate = mutableListOf<String>()
        val bloccate = mutableListOf<String>()

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = { esclusi ->
                    when {
                        "vecchia" !in esclusi -> {
                            tentativi.incrementAndGet()
                            throw OutOfMemoryError("di prova")
                        }
                        completate.isEmpty() -> {
                            completate += "recente"
                            RisultatoTentativo.Avviata("recente")
                        }
                        else -> RisultatoTentativo.Nessuno
                    }
                },
                ultimaTentata = { "vecchia" },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
            segnalaElaborazioneBloccata = { bloccate += it },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, tentativi.get(), "tre escape, poi esclusa")
        assertEquals(listOf("vecchia"), bloccate)
        assertEquals(listOf("recente"), completate, "gli altri elementi proseguono")
        scope.cancel()
    }

    @Test
    fun `AC-313 i tentativi sono distanziati da un back off crescente, mai a distanza zero`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val istanti = mutableListOf<Long>()

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = { esclusi ->
                    if ("id-1" in esclusi) {
                        RisultatoTentativo.Nessuno
                    } else {
                        istanti += testScheduler.currentTime
                        RisultatoTentativo.Rifiutata("id-1")
                    }
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, istanti.size)
        val distanze = istanti.zipWithNext { a, b -> b - a }
        assertTrue(distanze.all { it > 0 }, "mai un tentativo a distanza zero")
        assertTrue(distanze[1] > distanze[0], "il back-off cresce: ${distanze[0]} poi ${distanze[1]}")
        scope.cancel()
    }

    @Test
    fun `AC-314 due richieste concorrenti di avanzamento non eseguono mai due Elaborazioni insieme`() {
        val dentro = CountDownLatch(1)
        val procedi = CountDownLatch(1)
        val primaVolta = AtomicBoolean(true)
        val concorrenti = AtomicInteger(0)
        val massimoConcorrenti = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default + Job())

        val coda = CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = {
                    val n = concorrenti.incrementAndGet()
                    massimoConcorrenti.updateAndGet { max(it, n) }
                    if (primaVolta.compareAndSet(true, false)) {
                        dentro.countDown()
                        assertTrue(procedi.await(10, TimeUnit.SECONDS), "il test avrebbe dovuto sbloccare in tempo")
                    }
                    concorrenti.decrementAndGet()
                    RisultatoTentativo.Avviata("id-1")
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
        )
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
        scope.cancel() // il worker riprogramma sempre: senza annullare lo scope non terminerebbe mai da solo
        assertTrue(coda.fermaEAttendi(10_000))
    }

    @Test
    fun `rework item 3 - ferma e attendi interrompe una chiamata bloccata e il worker termina`() {
        val bloccato = CountDownLatch(1)
        val maiSbloccato = CountDownLatch(1) // mai contato: la chiamata resta bloccata finche' non e' interrotta
        val scope = CoroutineScope(Dispatchers.Default + Job())

        val coda = CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = {
                    bloccato.countDown()
                    maiSbloccato.await() // interrompibile: runInterruptible deve svegliarlo via Thread.interrupt()
                    error("mai raggiunto")
                },
                ultimaTentata = { null },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
        )
        assertTrue(bloccato.await(10, TimeUnit.SECONDS), "la chiamata bloccante avrebbe dovuto partire")

        scope.cancel() // passo 1: annulla lo scope
        val fermato = coda.fermaEAttendi(10_000) // passo 2: attende con un timeout

        assertTrue(fermato, "il worker termina perche' runInterruptible interrompe il thread bloccato")
    }

    @Test
    fun `rework item 4 - dopo un InterruptedException il flag viene pulito sul thread reale, non lasciato`() {
        val primaVolta = AtomicBoolean(true)
        val flagAllaSeconda = AtomicReference<Boolean>()
        val seconda = CountDownLatch(1)
        val scope = CoroutineScope(Dispatchers.Default + Job())

        CodaElaborazioni(
            scope = scope,
            fonte = FonteAvanzamento(
                prossima = {
                    if (primaVolta.compareAndSet(true, false)) throw InterruptedException("di prova")
                    flagAllaSeconda.set(Thread.currentThread().isInterrupted)
                    seconda.countDown()
                    RisultatoTentativo.Avviata("id-1")
                },
                ultimaTentata = { "id-1" },
            ),
            recuperaElaborazioniInterrotte = {},
            modelliPronti = { true },
        )

        assertTrue(seconda.await(10, TimeUnit.SECONDS), "il ritentativo dopo l'escape avrebbe dovuto partire")
        assertEquals(false, flagAllaSeconda.get(), "il flag di interruzione deve essere stato ripulito, non lasciato")
        scope.cancel()
    }
}
