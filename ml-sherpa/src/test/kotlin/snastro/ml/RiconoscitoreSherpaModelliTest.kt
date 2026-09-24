package snastro.ml

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import snastro.modelli.CartellaCacheModelli
import snastro.modelli.ID_ASR_PARAKEET_TDT_0_6B_V3_INT8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals

/**
 * AC-388 (ADR 0015 Consequences) against the REAL sherpa-onnx natives and the real Parakeet model:
 * `N` [RiconoscitoreSherpa.riconosci] calls with no [RiconoscitoreSherpa.chiudi] in between load the
 * model exactly once; [RiconoscitoreSherpa.chiudi] releases it and the next call reloads it (never a
 * load per call). The model directory defaults to `:modelli`'s real per-OS cache
 * (`asr-parakeet-tdt-0.6b-v3-int8`, ADR 0013); `SNASTRO_MODELLO_RICONOSCITORE` overrides it (e.g. an
 * already-extracted copy on a dev machine that has not run provisioning). A synthetic tone is enough
 * here: this test proves the LOAD-ONCE lifecycle, not recognition quality (that is AC-252, on the
 * real Parakeet adapter, `RiconoscitoreParlatoSherpaContrattoTest`, against real speech).
 */
@Tag("modelli")
class RiconoscitoreSherpaModelliTest {
    private fun modello(): ModelloTransducer {
        val dir = percorsoModelloParakeet()
        return ModelloTransducer(
            encoder = dir.resolve("encoder.int8.onnx"),
            decoder = dir.resolve("decoder.int8.onnx"),
            joiner = dir.resolve("joiner.int8.onnx"),
            tokens = dir.resolve("tokens.txt"),
        )
    }

    private fun tonoDiProva(durataMs: Long): CampioniAudio {
        val n = (durataMs * CAMPIONAMENTO_HZ / MS_PER_S).toInt()
        return CampioniAudio(
            FloatArray(n) { i -> (AMPIEZZA * sin(2 * PI * FREQUENZA_HZ * i / CAMPIONAMENTO_HZ)).toFloat() },
        )
    }

    @Test
    fun `AC-388 N riconosci senza chiudi caricano il modello sherpa una sola volta`() {
        val riconoscitore = RiconoscitoreSherpa(MotoreSherpa(), modello(), ConfigSessione.coreDiPrestazione())
        val campioni = tonoDiProva(DURATA_TONO_MS)

        riconoscitore.riconosci(campioni)
        riconoscitore.riconosci(campioni)
        riconoscitore.riconosci(campioni)

        assertEquals(1, riconoscitore.caricamenti)
    }

    @Test
    fun `AC-388 AC-254 chiudi rilascia il modello sherpa e la chiamata successiva lo ricarica`() {
        val riconoscitore = RiconoscitoreSherpa(MotoreSherpa(), modello(), ConfigSessione.coreDiPrestazione())
        val campioni = tonoDiProva(DURATA_TONO_MS)

        riconoscitore.riconosci(campioni)
        riconoscitore.chiudi()
        riconoscitore.riconosci(campioni)

        assertEquals(2, riconoscitore.caricamenti)
    }

    private companion object {
        const val CAMPIONAMENTO_HZ = 16_000
        const val MS_PER_S = 1_000L
        const val DURATA_TONO_MS = 500L
        const val FREQUENZA_HZ = 440.0
        const val AMPIEZZA = 0.2
    }
}

/**
 * The Parakeet model directory: `SNASTRO_MODELLO_RICONOSCITORE` overrides `:modelli`'s real per-OS
 * cache (ADR 0008 Amendment (c)) — e.g. an already-extracted copy for a dev machine that has not run
 * provisioning yet. `test_wavs/` (real speech, used by AC-252) lives inside the same directory.
 */
internal fun percorsoModelloParakeet(): Path {
    val override = System.getenv("SNASTRO_MODELLO_RICONOSCITORE")
    val dir = if (override != null) {
        Path.of(override)
    } else {
        CartellaCacheModelli.risolvi().resolve(ID_ASR_PARAKEET_TDT_0_6B_V3_INT8)
    }
    // L627a: the per-OS cache exists only after the app's onboarding provisioning has run; on a dev
    // machine that never ran it (or an unset/stale override) this must be a SKIP, not a native-load
    // failure with a confusing message.
    assumeTrue(Files.isDirectory(dir), "$dir non esiste: modello Parakeet non installato (provisioning non eseguito)")
    return dir
}
