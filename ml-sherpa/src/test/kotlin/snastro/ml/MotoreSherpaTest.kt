package snastro.ml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Gate tests: no native library is ever loaded here — the loader is a counting fake (AC-399). */
class MotoreSherpaTest {
    @TempDir
    lateinit var tmp: Path

    private val caricamenti = AtomicInteger()
    private val caricatoreFinto: () -> Unit = { caricamenti.incrementAndGet() }

    private fun cartellaConLibrerie(nome: String): Path {
        val cartella = Files.createDirectories(tmp.resolve(nome))
        LIBRERIE_NATIVE.forEach { Files.createFile(cartella.resolve(it)) }
        return cartella
    }

    private val config = ConfigSessione(percorsiModello = emptyList(), threadIntraOp = 1)

    @Test
    fun `AC-397 senza sherpa_onnx native path usa le risorse Compose e la imposta`() {
        val risorse = cartellaConLibrerie("risorse")
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to risorse.toString())

        MotoreSherpa(proprieta, caricatoreFinto).caricaNativi()

        assertEquals(risorse.toAbsolutePath().toString(), proprieta.leggi(PROPRIETA_PERCORSO_NATIVI))
        assertEquals(1, caricamenti.get())
    }

    @Test
    fun `AC-397 sherpa_onnx native path gia impostata vince su compose application resources dir`() {
        val nativi = cartellaConLibrerie("nativi")
        val risorse = cartellaConLibrerie("risorse")
        val proprieta = ProprietaSistemaFinte(
            PROPRIETA_PERCORSO_NATIVI to nativi.toString(),
            PROPRIETA_RISORSE_COMPOSE to risorse.toString(),
        )

        MotoreSherpa(proprieta, caricatoreFinto).caricaNativi()

        assertEquals(nativi.toAbsolutePath().toString(), proprieta.leggi(PROPRIETA_PERCORSO_NATIVI))
        assertEquals(1, caricamenti.get())
    }

    @Test
    fun `AC-397 senza nessuna delle due proprieta fallisce nominandole entrambe e non carica nulla`() {
        val errore = assertFailsWith<IllegalStateException> {
            MotoreSherpa(ProprietaSistemaFinte(), caricatoreFinto).caricaNativi()
        }

        assertTrue(PROPRIETA_PERCORSO_NATIVI in errore.message.orEmpty(), errore.message)
        assertTrue(PROPRIETA_RISORSE_COMPOSE in errore.message.orEmpty(), errore.message)
        assertEquals(0, caricamenti.get())
    }

    @Test
    fun `AC-397 una directory priva delle due librerie fallisce nominando le proprieta e non carica nulla`() {
        val vuota = Files.createDirectories(tmp.resolve("vuota"))
        Files.createFile(vuota.resolve(LIBRERIE_NATIVE.first())) // one lib alone is not enough
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to vuota.toString())

        val errore = assertFailsWith<IllegalStateException> {
            MotoreSherpa(proprieta, caricatoreFinto).caricaNativi()
        }

        assertTrue(PROPRIETA_PERCORSO_NATIVI in errore.message.orEmpty(), errore.message)
        assertTrue(PROPRIETA_RISORSE_COMPOSE in errore.message.orEmpty(), errore.message)
        assertTrue(LIBRERIE_NATIVE.last() in errore.message.orEmpty(), errore.message)
        assertEquals(0, caricamenti.get())
        assertEquals(null, proprieta.leggi(PROPRIETA_PERCORSO_NATIVI))
    }

    @Test
    fun `AC-397 java library path non e mai letta ne impostata`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())

        MotoreSherpa(proprieta, caricatoreFinto).conSessione(config) { }

        assertFalse("java.library.path" in proprieta.nomiToccati)
    }

    @Test
    fun `AC-398 due caricaNativi e due conSessione caricano i nativi una sola volta`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)

        motore.caricaNativi()
        motore.caricaNativi()
        motore.conSessione(config) { }
        motore.conSessione(config) { }

        assertEquals(1, caricamenti.get())
    }

    @Test
    fun `AC-398 un caricamento fallito non conta come caricato e si puo riprovare`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        var falliscePrimaVolta = true
        val motore = MotoreSherpa(proprieta) {
            caricamenti.incrementAndGet()
            if (falliscePrimaVolta) {
                falliscePrimaVolta = false
                throw UnsatisfiedLinkError("finto")
            }
        }

        assertFailsWith<UnsatisfiedLinkError> { motore.caricaNativi() }
        motore.caricaNativi()
        motore.caricaNativi()

        assertEquals(2, caricamenti.get())
    }

    @Test
    fun `AC-399 conSessione carica i nativi in modo pigro prima di consegnare la sessione`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)
        assertEquals(0, caricamenti.get(), "la costruzione non carica nulla")

        val caricatiDentroUso = motore.conSessione(config) { caricamenti.get() }

        assertEquals(1, caricatiDentroUso)
    }

    @Test
    fun `AC-399 se i nativi non si caricano conSessione non esegue uso`() {
        var eseguito = false

        assertFailsWith<IllegalStateException> {
            MotoreSherpa(ProprietaSistemaFinte(), caricatoreFinto).conSessione(config) { eseguito = true }
        }

        assertFalse(eseguito)
    }

    @Test
    fun `AC-244 conSessione restituisce il risultato e consegna la config`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val cpu4 = ConfigSessione(listOf(tmp.resolve("model.onnx")), threadIntraOp = 4)

        val vista = MotoreSherpa(proprieta, caricatoreFinto).conSessione(cpu4) { it.config }

        assertEquals(cpu4, vista)
    }

    @Test
    fun `AC-244 le risorse registrate sono rilasciate all uscita da conSessione anche su eccezione`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)
        val rilasciate = mutableListOf<String>()

        motore.conSessione(config) { s -> s.registra("a") { rilasciate += it } }
        assertEquals(listOf("a"), rilasciate)

        assertFailsWith<IllegalArgumentException> {
            motore.conSessione(config) { s ->
                s.registra("b") { rilasciate += it }
                throw IllegalArgumentException("uso fallito")
            }
        }
        assertEquals(listOf("a", "b"), rilasciate)
    }

    @Test
    fun `AC-401 due conSessione concorrenti non si sovrappongono mai`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)
        val dentro = AtomicInteger()
        val massimo = AtomicInteger()
        val primaDentro = CountDownLatch(1)
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val prima = esecutore.submit {
                motore.conSessione(config) {
                    massimo.accumulateAndGet(dentro.incrementAndGet(), ::maxOf)
                    primaDentro.countDown()
                    Thread.sleep(ATTESA_SOVRAPPOSIZIONE_MS)
                    dentro.decrementAndGet()
                }
            }
            assertTrue(primaDentro.await(TIMEOUT_S, TimeUnit.SECONDS))
            val seconda = esecutore.submit {
                motore.conSessione(config) {
                    massimo.accumulateAndGet(dentro.incrementAndGet(), ::maxOf)
                    dentro.decrementAndGet()
                }
            }
            prima.get(TIMEOUT_S, TimeUnit.SECONDS)
            seconda.get(TIMEOUT_S, TimeUnit.SECONDS)
        } finally {
            esecutore.shutdownNow()
        }

        assertEquals(1, massimo.get())
    }

    @Test
    fun `AC-401 il Mutex e rilasciato anche su eccezione`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)
        val esecutore = Executors.newSingleThreadExecutor()
        try {
            assertFailsWith<IllegalArgumentException> {
                motore.conSessione(config) { throw IllegalArgumentException("uso fallito") }
            }
            // Another thread gets the Mutex: it was released on the exception.
            val esito = esecutore.submit<String> { motore.conSessione(config) { "ok" } }

            assertEquals("ok", esito.get(TIMEOUT_S, TimeUnit.SECONDS))
        } finally {
            esecutore.shutdownNow()
        }
    }

    @Test
    fun `AC-401 una conSessione annidata nello stesso thread e rifiutata invece di sovrapporsi`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)

        assertFailsWith<IllegalStateException> {
            motore.conSessione(config) { motore.conSessione(config) { } }
        }
        assertEquals("libero", motore.conSessione(config) { "libero" })
    }

    @Test
    fun `AC-408 un thread interrotto mentre attende in conSessione riceve InterruptedException, il suo uso non gira`() {
        val proprieta = ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString())
        val motore = MotoreSherpa(proprieta, caricatoreFinto)
        val aDentro = CountDownLatch(1)
        val rilasciaA = CountDownLatch(1)
        val usoDiB = AtomicInteger()
        val erroreDiB = AtomicReference<Throwable>()
        val a = thread {
            motore.conSessione(config) {
                aDentro.countDown()
                rilasciaA.await()
            }
        }
        assertTrue(aDentro.await(TIMEOUT_S, TimeUnit.SECONDS))
        val caricamentiPrimaDiB = caricamenti.get()
        val b = thread {
            try {
                motore.conSessione(config) { usoDiB.incrementAndGet() }
            } catch (e: InterruptedException) {
                erroreDiB.set(e)
            }
        }
        attendiFinche { b.state == Thread.State.WAITING }

        b.interrupt()
        b.join(TIMEOUT_S * MS_PER_S)
        rilasciaA.countDown()
        a.join(TIMEOUT_S * MS_PER_S)

        assertIs<InterruptedException>(erroreDiB.get())
        assertEquals(0, usoDiB.get())
        assertEquals(caricamentiPrimaDiB, caricamenti.get(), "nessun nativo caricato per B")
        assertEquals("libero", motore.conSessione(config) { "libero" }, "A ha rilasciato normalmente")
    }

    @Test
    fun `AC-408 un thread gia interrotto non apre alcuna sessione`() {
        val motore = MotoreSherpa(
            ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString()),
            caricatoreFinto,
        )
        var eseguito = false

        Thread.currentThread().interrupt()
        try {
            assertFailsWith<InterruptedException> { motore.conSessione(config) { eseguito = true } }
        } finally {
            Thread.interrupted() // never leak the flag to the next test on this thread
        }

        assertFalse(eseguito)
        assertEquals(0, caricamenti.get())
    }

    @Test
    fun `AC-409 il Mutex e equo, B e C lo ottengono nell ordine in cui si sono accodati`() {
        val motore = MotoreSherpa(
            ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString()),
            caricatoreFinto,
        )
        val ordine = CopyOnWriteArrayList<String>()
        val aDentro = CountDownLatch(1)
        val rilasciaA = CountDownLatch(1)
        val a = thread {
            motore.conSessione(config) {
                aDentro.countDown()
                rilasciaA.await()
            }
        }
        assertTrue(aDentro.await(TIMEOUT_S, TimeUnit.SECONDS))
        val b = thread { motore.conSessione(config) { ordine += "B" } }
        attendiFinche { b.state == Thread.State.WAITING }
        val c = thread { motore.conSessione(config) { ordine += "C" } }
        attendiFinche { c.state == Thread.State.WAITING }

        rilasciaA.countDown()
        listOf(a, b, c).forEach { it.join(TIMEOUT_S * MS_PER_S) }

        assertEquals(listOf("B", "C"), ordine)
    }

    @Test
    fun `AC-409 la pipeline che richiede di nuovo il Mutex si accoda dietro un estrazione in attesa`() {
        val motore = MotoreSherpa(
            ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie("r").toString()),
            caricatoreFinto,
        )
        val ordine = CopyOnWriteArrayList<String>()
        val pipelineDentro = CountDownLatch(1)
        val estrazioneInCoda = CountDownLatch(1)
        val pipeline = thread {
            motore.conSessione(config) {
                pipelineDentro.countDown()
                estrazioneInCoda.await()
                ordine += "pipeline 1"
            }
            motore.conSessione(config) { ordine += "pipeline 2" } // the next call, straight after the release
        }
        assertTrue(pipelineDentro.await(TIMEOUT_S, TimeUnit.SECONDS))
        val estrazione = thread { motore.conSessione(config) { ordine += "estrazione" } }
        attendiFinche { estrazione.state == Thread.State.WAITING }

        estrazioneInCoda.countDown()
        listOf(pipeline, estrazione).forEach { it.join(TIMEOUT_S * MS_PER_S) }

        assertEquals(listOf("pipeline 1", "estrazione", "pipeline 2"), ordine)
    }

    private fun attendiFinche(condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + TIMEOUT_S * MS_PER_S
        while (!condizione()) {
            check(System.currentTimeMillis() < scadenza) { "timeout: il thread non e in attesa sul Mutex" }
            Thread.sleep(PASSO_ATTESA_MS)
        }
    }

    private companion object {
        const val PROPRIETA_PERCORSO_NATIVI = "sherpa_onnx.native.path"
        const val PROPRIETA_RISORSE_COMPOSE = "compose.application.resources.dir"
        val LIBRERIE_NATIVE = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)
        const val ATTESA_SOVRAPPOSIZIONE_MS = 200L
        const val TIMEOUT_S = 10L
        const val MS_PER_S = 1_000L
        const val PASSO_ATTESA_MS = 5L
    }
}
