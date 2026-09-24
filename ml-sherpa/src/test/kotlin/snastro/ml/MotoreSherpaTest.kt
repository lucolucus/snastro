package snastro.ml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

    private companion object {
        const val PROPRIETA_PERCORSO_NATIVI = "sherpa_onnx.native.path"
        const val PROPRIETA_RISORSE_COMPOSE = "compose.application.resources.dir"
        val LIBRERIE_NATIVE = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)
        const val ATTESA_SOVRAPPOSIZIONE_MS = 200L
        const val TIMEOUT_S = 10L
    }
}
