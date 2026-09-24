package snastro.ml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.CampioniAudio
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gate tests (AC-388, AC-33, AC-254): no native library or real recognizer is ever loaded here —
 * [carica] is a counting fake, and [OfflineRecognizerConfig][com.k2fsa.sherpa.onnx.OfflineRecognizerConfig]
 * itself is pure Java (no native method), so building it is safe without natives.
 */
class RiconoscitoreSherpaTest {
    @TempDir
    lateinit var tmp: Path

    private val modello = ModelloTransducer(
        encoder = Path.of("encoder.onnx"),
        decoder = Path.of("decoder.onnx"),
        joiner = Path.of("joiner.onnx"),
        tokens = Path.of("tokens.txt"),
    )

    private fun cartellaConLibrerie(): Path {
        val cartella = Files.createDirectories(tmp.resolve("risorse"))
        LIBRERIE_NATIVE.forEach { Files.createFile(cartella.resolve(it)) }
        return cartella
    }

    private fun motore(): MotoreSherpa =
        MotoreSherpa(ProprietaSistemaFinte(PROPRIETA_RISORSE_COMPOSE to cartellaConLibrerie().toString())) {}

    private class MotoreRiconoscimentoFinto(private val risultato: RisultatoRiconoscimento) : MotoreRiconoscimento {
        var decodifiche: Int = 0
            private set
        var rilasciato: Boolean = false
            private set

        override fun decodifica(campioni: CampioniAudio): RisultatoRiconoscimento {
            decodifiche++
            return risultato
        }

        override fun rilascia() {
            rilasciato = true
        }
    }

    @Test
    fun `AC-388 N chiamate a riconosci senza chiudi caricano il modello una sola volta`() {
        var caricamenti = 0
        val caricati = mutableListOf<MotoreRiconoscimentoFinto>()
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") { _ ->
            caricamenti++
            MotoreRiconoscimentoFinto(RisultatoRiconoscimento("ciao", null)).also(caricati::add)
        }
        val campioni = CampioniAudio(FloatArray(160) { 0.1f })

        val r1 = riconoscitore.riconosci(campioni)
        riconoscitore.riconosci(campioni)
        val r3 = riconoscitore.riconosci(campioni)

        assertEquals(1, caricamenti)
        assertEquals(3, caricati.single().decodifiche)
        assertEquals(RisultatoRiconoscimento("ciao", null), r1)
        assertEquals(r1, r3)
    }

    @Test
    fun `AC-388 AC-254 chiudi rilascia il modello caricato e la chiamata successiva lo ricarica`() {
        var caricamenti = 0
        val caricati = mutableListOf<MotoreRiconoscimentoFinto>()
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") { _ ->
            caricamenti++
            MotoreRiconoscimentoFinto(RisultatoRiconoscimento("", null)).also(caricati::add)
        }
        val campioni = CampioniAudio(FloatArray(160) { 0.1f })

        riconoscitore.riconosci(campioni)
        riconoscitore.chiudi()
        riconoscitore.riconosci(campioni)

        assertEquals(2, caricamenti)
        assertTrue(caricati[0].rilasciato)
        assertFalse(caricati[1].rilasciato)
    }

    @Test
    fun `AC-254 chiudi senza aver mai caricato non fa nulla`() {
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") {
            error("chiudi senza caricamenti precedenti non deve caricare il modello")
        }

        riconoscitore.chiudi() // no exception
    }

    @Test
    fun `AC-33 campioni vuoti non caricano nulla e restituiscono testo vuoto senza token`() {
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") {
            error("campioni vuoti non devono caricare il modello")
        }

        val r = riconoscitore.riconosci(CampioniAudio(FloatArray(0)))

        assertEquals(RisultatoRiconoscimento("", null), r)
    }

    /**
     * fix-batch-16 MED-2: a fake native handle that RECORDS every use after its release — a decode
     * on it, or a second release (both a SIGSEGV on the real `OfflineRecognizer`). The first decode
     * can be held open ([bloccaPrimaDecodifica]) so other threads pile up on the native Mutex.
     */
    private class ManigliaCheRegistraUsoDopoRilascio(
        private val violazioni: AtomicInteger,
        private val bloccaPrimaDecodifica: () -> Unit,
    ) : MotoreRiconoscimento {
        @Volatile private var rilasciata = false

        override fun decodifica(campioni: CampioniAudio): RisultatoRiconoscimento {
            if (rilasciata) violazioni.incrementAndGet()
            bloccaPrimaDecodifica()
            return RisultatoRiconoscimento("ciao", null)
        }

        override fun rilascia() {
            if (rilasciata) violazioni.incrementAndGet()
            rilasciata = true
        }
    }

    @Test
    fun `fix-batch-16 chiudi e riconosci concorrenti non toccano mai un modello gia rilasciato`() {
        val violazioni = AtomicInteger()
        val inDecodifica = CountDownLatch(1)
        val sblocca = CountDownLatch(1)
        val primaDecodifica = AtomicBoolean(true)
        val riconoscitore = RiconoscitoreSherpa(motore(), modello, threadIntraOp = 1, provider = "cpu") { _ ->
            ManigliaCheRegistraUsoDopoRilascio(violazioni) {
                if (primaDecodifica.getAndSet(false)) {
                    inDecodifica.countDown()
                    sblocca.await()
                }
            }
        }
        val campioni = CampioniAudio(FloatArray(160) { 0.1f })

        // Two chiudi queued on the Mutex while a decode holds it: each must see the model's state
        // INSIDE the Mutex — never both release the same handle they read before waiting.
        val decodifica = thread { riconoscitore.riconosci(campioni) }
        inDecodifica.await()
        val chiusure = List(2) { thread { riconoscitore.chiudi() } }
        attendiFinche { chiusure.all { it.state == Thread.State.WAITING } }
        sblocca.countDown()
        (chiusure + decodifica).forEach(Thread::join)

        // Then a free-for-all: decodes and releases interleaved on several threads.
        val lavoratori = List(4) { n ->
            thread {
                repeat(ITERAZIONI_CONCORRENTI) { i ->
                    if ((i + n) % 3 == 0) riconoscitore.chiudi() else riconoscitore.riconosci(campioni)
                }
            }
        }
        lavoratori.forEach(Thread::join)

        assertEquals(0, violazioni.get(), "un modello rilasciato e' stato usato o rilasciato di nuovo")
    }

    private fun attendiFinche(condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + ATTESA_MASSIMA_MS
        while (!condizione()) {
            check(System.currentTimeMillis() < scadenza) { "timeout: i thread non sono in attesa sul Mutex" }
            Thread.sleep(PASSO_ATTESA_MS)
        }
    }

    private companion object {
        const val ITERAZIONI_CONCORRENTI = 300
        const val ATTESA_MASSIMA_MS = 5_000L
        const val PASSO_ATTESA_MS = 5L
        const val PROPRIETA_RISORSE_COMPOSE = "compose.application.resources.dir"
        val LIBRERIE_NATIVE = listOf("onnxruntime", "sherpa-onnx-jni").map(System::mapLibraryName)
    }
}
