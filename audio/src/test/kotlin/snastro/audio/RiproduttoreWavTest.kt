package snastro.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiproduttoreWavTest {
    // --- State before any playback: no device opened, safe for the default gate -----------------

    @Test
    fun `un riproduttore appena creato non e in riproduzione e parte da 0`() {
        RiproduttoreWav().use { r ->
            assertFalse(r.inRiproduzione())
            assertEquals(0, r.posizioneMs())
        }
    }

    @Test
    fun `pausa su un riproduttore mai avviato non fa nulla`() {
        RiproduttoreWav().use { r ->
            r.pausa()
            assertFalse(r.inRiproduzione())
        }
    }

    // --- Real playback: opens a SourceDataLine, needs an audio device — opt-in ------------------

    @Test
    @Tag("modelli")
    fun `AC-125 riproduci imposta subito la posizione all offset richiesto`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 1_000)

        RiproduttoreWav().use { r ->
            r.riproduci(wav, intervalli = null, daMs = 250)
            assertEquals(250, r.posizioneMs())
        }
    }

    @Test
    @Tag("modelli")
    fun `AC-125 una lista di intervalli si riproduce in sequenza fermandosi alla fine dell ultimo`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 1_000)
        val intervalli = listOf(0L to 150L, 400L to 500L)
        val durataSequenza = intervalli.sumOf { it.second - it.first } // 250 ms

        RiproduttoreWav().use { r ->
            r.riproduci(wav, intervalli, daMs = 0)
            val fermo = attendiFinche(TIMEOUT_MS) { !r.inRiproduzione() }

            assertTrue(fermo, "la riproduzione doveva fermarsi da sola alla fine dell'ultimo intervallo")
            assertEquals(durataSequenza, r.posizioneMs())
        }
    }

    @Test
    @Tag("modelli")
    fun `AC-125 pausa ferma la riproduzione e ne conserva la posizione`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 3_000)

        RiproduttoreWav().use { r ->
            r.riproduci(wav, intervalli = null, daMs = 0)
            Thread.sleep(ATTESA_IN_RIPRODUZIONE_MS)
            r.pausa()

            assertFalse(r.inRiproduzione())
            assertTrue(r.posizioneMs() > 0, "posizione dopo la pausa: ${r.posizioneMs()}")
        }
    }

    private fun attendiFinche(timeoutMs: Long, condizione: () -> Boolean): Boolean {
        val scadenza = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < scadenza) {
            if (condizione()) return true
            Thread.sleep(ATTESA_POLLING_MS)
        }
        return condizione()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val ATTESA_POLLING_MS = 20L
        const val ATTESA_IN_RIPRODUZIONE_MS = 300L
    }
}
