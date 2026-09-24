package snastro.ml

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs

/**
 * sherpa-onnx `OfflineRecognizer` over a NeMo transducer model (boundary `tec-ml-sherpa`, ADR
 * 0004/0013/0016): `nemo_transducer` model type, greedy decoding, CPU by default (ADR 0013).
 *
 * **Lifecycle (ADR 0015, AC-388).** [MotoreSherpa.conSessione] protects every native call — including
 * the FIRST [riconosci], which loads the model — but is never held BETWEEN two calls: each call opens
 * and closes its own session, so this never conflicts with a sibling ML adapter's own session on the
 * same thread (they run sequentially, never nested, on the single pipeline dispatcher, ADR 0004 —
 * `conSessione` is not reentrant). What survives from one call to the next is the loaded model itself,
 * cached in-process — not tied to any one session's lifetime. `N` calls to [riconosci] therefore cost
 * exactly ONE load; [chiudi] releases it explicitly (idempotent) so a caller can scope one instance to
 * one Elaborazione — the model is loaded again, lazily, on the next call after [chiudi].
 */
class RiconoscitoreSherpa internal constructor(
    private val motore: MotoreSherpa,
    private val modello: ModelloTransducer,
    private val threadIntraOp: Int,
    private val provider: String,
    private val carica: (OfflineRecognizerConfig) -> MotoreRiconoscimento,
) {
    constructor(
        motore: MotoreSherpa,
        modello: ModelloTransducer,
        threadIntraOp: Int,
        provider: String = ConfigSessione.PROVIDER_CPU,
    ) : this(motore, modello, threadIntraOp, provider, ::MotoreRiconoscimentoReale)

    @Volatile
    private var caricato: MotoreRiconoscimento? = null

    /** How many times the model was actually loaded — test-only evidence for AC-388. */
    internal var caricamenti: Int = 0
        private set

    /** Empty [campioni] give a blank result with no native call at all (never a load, AC-33/AC-388). */
    fun riconosci(campioni: CampioniAudio): RisultatoRiconoscimento {
        if (campioni.campioni.isEmpty()) return RisultatoRiconoscimento("", null)
        return motore.conSessione(configSessione()) { caricatoOra().decodifica(campioni) }
    }

    /**
     * Releases the cached model, if any (AC-254/388): idempotent, the next [riconosci] reloads it.
     * fix-batch-16 MED-2: [caricato] is read, cleared and released all INSIDE the native Mutex — a
     * handle read outside it could be released twice by two concurrent callers, or decoded by another
     * thread between its release and the clearing (a use-after-free on the native pointer, a JVM
     * SIGSEGV). The unlocked read is only a fast path for "never loaded" (no session, no native load).
     */
    fun chiudi() {
        if (caricato == null) return
        motore.conSessione(configSessione()) { sessione ->
            val modelloCaricato = caricato ?: return@conSessione
            caricato = null
            sessione.registra(modelloCaricato) { it.rilascia() } // released as the session closes, Mutex still held
        }
    }

    private fun caricatoOra(): MotoreRiconoscimento =
        caricato ?: carica(configRecognizer()).also {
            caricato = it
            caricamenti++
        }

    // Purely descriptive (MotoreSherpa/SessioneSherpa never read percorsiModello themselves): keeps
    // this session's config consistent with what it actually loads, for future debugging/observability.
    private fun configSessione(): ConfigSessione = ConfigSessione(
        percorsiModello = listOf(modello.encoder, modello.decoder, modello.joiner, modello.tokens),
        threadIntraOp = threadIntraOp,
        provider = provider,
    )

    private fun configRecognizer(): OfflineRecognizerConfig = OfflineRecognizerConfig.builder()
        .setOfflineModelConfig(
            OfflineModelConfig.builder()
                .setTransducer(
                    OfflineTransducerModelConfig.builder()
                        .setEncoder(modello.encoder.toString())
                        .setDecoder(modello.decoder.toString())
                        .setJoiner(modello.joiner.toString())
                        .build(),
                )
                .setTokens(modello.tokens.toString())
                .setNumThreads(threadIntraOp)
                .setProvider(provider)
                .setModelType(MODEL_TYPE_NEMO_TRANSDUCER)
                .setDebug(false)
                .build(),
        )
        .setDecodingMethod(DECODING_METHOD_GREEDY)
        .build()

    private companion object {
        const val MODEL_TYPE_NEMO_TRANSDUCER = "nemo_transducer"
        const val DECODING_METHOD_GREEDY = "greedy_search"
    }
}

/**
 * Abstraction over one loaded recognizer: real natives behind [MotoreRiconoscimentoReale], a
 * hand-written fake in [RiconoscitoreSherpa]'s own gate test. [OfflineRecognizer] itself is a final,
 * native-backed third-party class — never fake-able, hence this seam (mirrors [MotoreSherpa]'s own
 * injectable `caricaLibrerie`).
 */
internal interface MotoreRiconoscimento {
    fun decodifica(campioni: CampioniAudio): RisultatoRiconoscimento

    fun rilascia()
}

private class MotoreRiconoscimentoReale(config: OfflineRecognizerConfig) : MotoreRiconoscimento {
    private val recognizer = OfflineRecognizer(config)

    override fun decodifica(campioni: CampioniAudio): RisultatoRiconoscimento {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(campioni.campioni, CAMPIONAMENTO_HZ)
            recognizer.decode(stream)
            val risultato = recognizer.getResult(stream)
            return RisultatoRiconoscimento(risultato.text.trim(), token(risultato))
        } finally {
            stream.release()
        }
    }

    override fun rilascia() {
        recognizer.release()
    }

    // Timestamps relative to the stream (ADR 0013): a token shorter than 1 ms after rounding still
    // gets a strictly-positive width (kernel IntervalloMs requires inizioMs < fineMs).
    private fun token(r: OfflineRecognizerResult): List<TokenRiconosciuto>? {
        val testi = r.tokens
        val inizi = r.timestamps
        val durate = r.durations
        if (testi == null || inizi == null || durate == null) return null
        val n = minOf(testi.size, inizi.size, durate.size)
        return (0 until n).map { i ->
            val inizioMs = (inizi[i] * MS_PER_S).toLong().coerceAtLeast(0)
            val fineMs = maxOf(inizioMs + 1, inizioMs + (durate[i] * MS_PER_S).toLong())
            TokenRiconosciuto(testi[i], IntervalloMs(inizioMs, fineMs))
        }
    }

    private companion object {
        const val CAMPIONAMENTO_HZ = 16_000
        const val MS_PER_S = 1_000
    }
}
