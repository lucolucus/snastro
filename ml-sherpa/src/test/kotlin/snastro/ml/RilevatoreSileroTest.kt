package snastro.ml

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * Gate test (no native library ever loaded): [RilevatoreSilero.configurazione] only assembles
 * plain `VadModelConfig`/`SileroVadModelConfig` builders (no native method on them), so AC-389 is
 * checked here without `@Tag("modelli")` — the real detection behaviour is `@Tag("modelli")`,
 * `VadSileroTest` (`:trascrizione:adattatori`).
 */
class RilevatoreSileroTest {
    @Test
    fun `AC-389 soglia 0_5, silenzio minimo 0_25 s, parlato massimo 25 s`() {
        val config = ConfigSessione(percorsiModello = listOf(Path.of("silero_vad.onnx")), threadIntraOp = 3)

        val silero = RilevatoreSilero.configurazione(config).sileroVadModelConfig

        assertEquals(RilevatoreSilero.SOGLIA, silero.threshold)
        assertEquals(RilevatoreSilero.SILENZIO_MINIMO_S, silero.minSilenceDuration)
        assertEquals(RilevatoreSilero.PARLATO_MASSIMO_S, silero.maxSpeechDuration)
    }

    @Test
    fun `la configurazione porta il percorso del modello, i thread, il provider e la frequenza di 16 kHz`() {
        val config = ConfigSessione(
            percorsiModello = listOf(Path.of("/tmp/silero_vad.onnx")),
            threadIntraOp = 5,
            provider = "cpu",
        )

        val vadModelConfig = RilevatoreSilero.configurazione(config)

        assertEquals("/tmp/silero_vad.onnx", vadModelConfig.sileroVadModelConfig.model)
        assertEquals(5, vadModelConfig.numThreads)
        assertEquals("cpu", vadModelConfig.provider)
        assertEquals(16_000, vadModelConfig.sampleRate)
    }
}
