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
import java.time.Instant
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
 * [CodaCondivisa] green-on-its-own with a SINGLE source bound (ADR 0023, AC-S55): AC-233/234/235/
 * 312/313/314 are ported UNCHANGED in intent, over one [FonteCoda] (Elaborazione) — every scenario is
 * driven by scripted lambdas, no fake port/repository needed (`avvio/src/main` never imports a
 * context's own types). Time-based scenarios run on [StandardTestDispatcher] with `advanceTimeBy`,
 * never a real sleep; the concurrency guarantee (AC-314) and `fermaEAttendi` run on the REAL dedicated
 * single-thread dispatcher with real threads, proven with latches. The N-source mechanics (ADR 0023
 * AC-S56..S63) are [CodaCondivisaMultiSorgenteTest]'s own concern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodaCondivisaTest {

    // --- AC-S55: the existing single-source (Elaborazione) scenarios, unchanged behaviour ----------

    @Test
    fun `AC-233 il recupero gira prima di ogni tentativo di avanzamento`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val ordine = mutableListOf<String>()
        // teste() offre una testa UNA SOLA volta (poi null, "esaurita"): prossima() la trova comunque
        // priva di esito (Nessuno, il caso di questo test) — consistente, mai una fuga senza fine di
        // riavvii immediati (AC-S59 li riserva a UN mancato allineamento, non a una fonte vuota per sempre).
        val giaOfferta = AtomicBoolean(false)

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = {
                        val offerta = giaOfferta.compareAndSet(false, true)
                        if (offerta) ElementoInCoda("id-1", "reg-1", Instant.EPOCH) else null
                    },
                    prossima = { _, _ ->
                        ordine += "esegui"
                        RisultatoTentativo.Nessuno
                    },
                    ultimaTentata = { null },
                    recupera = { ordine += "recupera" },
                    trattenuta = { false },
                ),
            ),
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { esclusi ->
                        rimanenti.firstOrNull { it !in esclusi }?.let { ElementoInCoda(it, "reg-$it", Instant.EPOCH) }
                    },
                    prossima = { esclusi, _ ->
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
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    // Rifiutata (mai Nessuno): un rifiuto passa sempre per il back-off, mai un riavvio
                    // immediato (AC-S59) — questa fonte resta "eligibile per sempre" apposta, e un
                    // riavvio immediato su una testa sempre presente girerebbe a vuoto senza fine.
                    prossima = { _, _ ->
                        chiamate.incrementAndGet()
                        RisultatoTentativo.Rifiutata("id-1")
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { !pronti },
                ),
            ),
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

            CodaCondivisa(
                scope = scope,
                fonti = listOf(
                    FonteCoda(
                        tipo = TipoElementoCoda.ELABORAZIONE,
                        // consistente con `completate`: una volta avviato, la testa sparisce (mai piu'
                        // idonea) — altrimenti un riavvio immediato (AC-S59) su una testa "eligibile per
                        // sempre" ma gia' conclusa girerebbe a vuoto senza fine.
                        teste = { if (completate.isEmpty()) ElementoInCoda("id-1", "reg-1", Instant.EPOCH) else null },
                        prossima = { _, _ ->
                            if (primaVolta.compareAndSet(true, false)) throw guasto
                            if (completate.isEmpty()) {
                                completate += "id-1"
                                RisultatoTentativo.Avviata("id-1")
                            } else {
                                RisultatoTentativo.Nessuno
                            }
                        },
                        ultimaTentata = { "id-1" },
                        recupera = { recuperi.incrementAndGet() },
                        trattenuta = { false },
                    ),
                ),
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { esclusi ->
                        when {
                            "vecchia" !in esclusi -> ElementoInCoda("vecchia", "reg-vecchia", Instant.EPOCH)
                            completate.isEmpty() -> ElementoInCoda("recente", "reg-recente", Instant.EPOCH)
                            else -> null
                        }
                    },
                    prossima = { esclusi, _ ->
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
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
            segnalaBloccato = { bloccate += it },
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { esclusi ->
                        when {
                            "vecchia" !in esclusi -> ElementoInCoda("vecchia", "reg-vecchia", Instant.EPOCH)
                            completate.isEmpty() -> ElementoInCoda("recente", "reg-recente", Instant.EPOCH)
                            else -> null
                        }
                    },
                    prossima = { esclusi, _ ->
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
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
            segnalaBloccato = { bloccate += it },
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { esclusi ->
                        if ("id-1" in esclusi) null else ElementoInCoda("id-1", "reg-1", Instant.EPOCH)
                    },
                    prossima = { esclusi, _ ->
                        if ("id-1" in esclusi) {
                            RisultatoTentativo.Nessuno
                        } else {
                            istanti += testScheduler.currentTime
                            RisultatoTentativo.Rifiutata("id-1")
                        }
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
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
    fun `AC-314 due richieste concorrenti di avanzamento non eseguono mai due elementi insieme`() {
        val dentro = CountDownLatch(1)
        val procedi = CountDownLatch(1)
        val primaVolta = AtomicBoolean(true)
        val concorrenti = AtomicInteger(0)
        val massimoConcorrenti = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default + Job())

        val coda = CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ ->
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
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
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

        val coda = CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ ->
                        bloccato.countDown()
                        maiSbloccato.await() // interrompibile: runInterruptible deve svegliarlo via Thread.interrupt()
                        error("mai raggiunto")
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
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

        CodaCondivisa(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ ->
                        if (primaVolta.compareAndSet(true, false)) throw InterruptedException("di prova")
                        flagAllaSeconda.set(Thread.currentThread().isInterrupted)
                        seconda.countDown()
                        RisultatoTentativo.Avviata("id-1")
                    },
                    ultimaTentata = { "id-1" },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
        )

        assertTrue(seconda.await(10, TimeUnit.SECONDS), "il ritentativo dopo l'escape avrebbe dovuto partire")
        assertEquals(false, flagAllaSeconda.get(), "il flag di interruzione deve essere stato ripulito, non lasciato")
        scope.cancel()
    }
}
