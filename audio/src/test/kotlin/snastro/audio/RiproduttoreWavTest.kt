package snastro.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.pausaInTempoReale
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
            attendiFinche(messaggio = "la riproduzione si ferma da sola alla fine dell'ultimo intervallo") {
                !r.inRiproduzione()
            }
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
            pausaInTempoReale(
                ATTESA_IN_RIPRODUZIONE_MS.milliseconds,
                motivo = "la posizione della linea audio reale deve avanzare",
            )
            r.pausa()

            assertFalse(r.inRiproduzione())
            assertTrue(r.posizioneMs() > 0, "posizione dopo la pausa: ${r.posizioneMs()}")
        }
    }

    private companion object {
        const val ATTESA_IN_RIPRODUZIONE_MS = 300L
    }
}
