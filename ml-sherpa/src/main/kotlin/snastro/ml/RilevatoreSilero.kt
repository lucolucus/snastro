package snastro.ml

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero speech detector via sherpa-onnx (boundary tec-ml-sherpa, com.k2fsa confinement, ADR
 * 0004/0016): the ONLY place `VadModelConfig`/`SileroVadModelConfig`/the native `Vad` are built.
 * Registers the native detector in [sessione] — released with the session (RC-5, AC-257, block
 * `vad-silero`). Tuned as measured by ADR 0015 regola 3: threshold [SOGLIA], min silence
 * [SILENZIO_MINIMO_S] s, max speech [PARLATO_MASSIMO_S] s (AC-389); every other Silero setting
 * keeps the library default (`SileroVadModelConfig.Builder`'s own defaults already match [SOGLIA]
 * and [SILENZIO_MINIMO_S] — set explicitly anyway, so a library default change can never drift
 * silently).
 */
class RilevatoreSilero(sessione: SessioneSherpa) {
    private val nativo: Vad = sessione.registra(Vad(configurazione(sessione.config))) { it.release() }

    /** Speech segments of [campioni] as (start sample, sample count), time-ordered (AC-255). */
    fun segmenti(campioni: FloatArray): List<SegmentoSilero> {
        nativo.reset() // one detector per session (ADR 0004): no state leaks between calls
        val trovati = mutableListOf<SegmentoSilero>()
        var indice = 0
        while (indice < campioni.size) {
            val fine = minOf(indice + FINESTRA_CAMPIONI, campioni.size)
            nativo.acceptWaveform(campioni.copyOfRange(indice, fine))
            drena(trovati)
            indice = fine
        }
        nativo.flush()
        drena(trovati)
        return trovati
    }

    private fun drena(trovati: MutableList<SegmentoSilero>) {
        while (!nativo.empty()) {
            val segmento = nativo.front()
            trovati += SegmentoSilero(segmento.start, segmento.samples.size)
            nativo.pop()
        }
    }

    companion object {
        /** ADR 0015 regola 3 (block `vad-silero`, AC-389). */
        const val SOGLIA = 0.5f
        const val SILENZIO_MINIMO_S = 0.25f
        const val PARLATO_MASSIMO_S = 25f

        private const val FINESTRA_CAMPIONI = 512
        private const val FREQUENZA_CAMPIONAMENTO = 16_000

        /**
         * Pure config assembly (no native call, AC-389): `VadModelConfig`/`SileroVadModelConfig`
         * are plain builders with no native method, so this is gate-testable without loading any
         * native library — see `RilevatoreSileroTest`.
         */
        internal fun configurazione(config: ConfigSessione): VadModelConfig =
            VadModelConfig.builder()
                .setSileroVadModelConfig(
                    SileroVadModelConfig.builder()
                        .setModel(config.percorsiModello.single().toString())
                        .setThreshold(SOGLIA)
                        .setMinSilenceDuration(SILENZIO_MINIMO_S)
                        .setMaxSpeechDuration(PARLATO_MASSIMO_S)
                        .build(),
                )
                .setSampleRate(FREQUENZA_CAMPIONAMENTO)
                .setNumThreads(config.threadIntraOp)
                .setProvider(config.provider)
                .setDebug(false)
                .build()
    }
}

/** One speech segment sherpa found: [campioneIniziale] and [numeroCampioni] are SAMPLE counts, not ms. */
data class SegmentoSilero(val campioneIniziale: Int, val numeroCampioni: Int)
