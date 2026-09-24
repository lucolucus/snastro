package snastro.ml

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.nio.file.Path

/**
 * sherpa-onnx `SpeakerEmbeddingExtractor` (boundary `tec-ml-sherpa`, ADR 0019 §1.4): the embedding of
 * ONE sample array (16 kHz mono float) per [calcola]. The ONE implementation shared by
 * `DiarizzatoreSherpa` (one call per piece) and `EstrattoreImprontaSherpa` (one call per print), so
 * both contexts compute embeddings the same way. The raw model output is returned, not normalized.
 *
 * **Lifecycle (the [RiconoscitoreSherpa] pattern, ADR 0017 §1).** Every [calcola] is exactly ONE
 * [MotoreSherpa.conSessione]: the native Mutex is held for that one call and free between two calls.
 * The model is loaded inside the first call's session, cached across calls, and released by [close]
 * (idempotent) — the next [calcola] reloads it. Each adapter owns its own instance.
 */
public class EmbeddingSherpa internal constructor(
    private val motore: MotoreSherpa,
    private val percorsoModello: Path,
    private val threadIntraOp: Int,
    private val carica: (SpeakerEmbeddingExtractorConfig) -> EstrattoreEmbedding,
) : AutoCloseable {
    public constructor(motore: MotoreSherpa, percorsoModello: Path, threadIntraOp: Int) :
        this(motore, percorsoModello, threadIntraOp, ::EstrattoreEmbeddingReale)

    private val configSessione = ConfigSessione(listOf(percorsoModello), threadIntraOp)

    @Volatile
    private var caricato: EstrattoreEmbedding? = null

    /** How many times the model was actually loaded — test-only evidence for AC-491. */
    internal var caricamenti: Int = 0
        private set

    /**
     * The embedding of [campioni]: constant dimension for one model. Audio too short for the model
     * gives a zero vector of that dimension (sherpa's `isReady` false), never a native fault.
     */
    public fun calcola(campioni: FloatArray): FloatArray =
        motore.conSessione(configSessione) { caricatoOra().calcola(campioni) }

    /**
     * Releases the cached model, if any: idempotent. Read, cleared and released INSIDE the native Mutex
     * (fix-batch-16 MED-2, as [RiconoscitoreSherpa.chiudi]): never a use-after-free on the native handle.
     */
    override fun close() {
        if (caricato == null) return
        motore.conSessione(configSessione) { sessione ->
            val modello = caricato ?: return@conSessione
            caricato = null
            sessione.registra(modello) { it.rilascia() }
        }
    }

    private fun caricatoOra(): EstrattoreEmbedding =
        caricato ?: carica(configEstrattore()).also {
            caricato = it
            caricamenti++
        }

    private fun configEstrattore(): SpeakerEmbeddingExtractorConfig = SpeakerEmbeddingExtractorConfig.builder()
        .setModel(percorsoModello.toString())
        .setNumThreads(threadIntraOp)
        .setProvider(configSessione.provider)
        .setDebug(false)
        .build()
}

/**
 * One loaded embedding model: real natives behind [EstrattoreEmbeddingReale], a fake in the gate tests
 * (`SpeakerEmbeddingExtractor` is a final native-backed class — never fake-able, hence this seam).
 */
internal interface EstrattoreEmbedding {
    fun calcola(campioni: FloatArray): FloatArray

    fun rilascia()
}

private class EstrattoreEmbeddingReale(config: SpeakerEmbeddingExtractorConfig) : EstrattoreEmbedding {
    private val estrattore = SpeakerEmbeddingExtractor(config)

    override fun calcola(campioni: FloatArray): FloatArray {
        val stream = estrattore.createStream()
        try {
            stream.acceptWaveform(campioni, CAMPIONAMENTO_HZ)
            stream.inputFinished()
            return if (estrattore.isReady(stream)) estrattore.compute(stream) else FloatArray(estrattore.dim)
        } finally {
            stream.release()
        }
    }

    override fun rilascia() {
        estrattore.release()
    }

    private companion object {
        const val CAMPIONAMENTO_HZ = 16_000
    }
}
