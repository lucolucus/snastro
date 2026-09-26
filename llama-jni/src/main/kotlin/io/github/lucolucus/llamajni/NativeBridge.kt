package io.github.lucolucus.llamajni

/**
 * The seam behind every `external fun` (implemented by [JniBridge]): all the Kotlin logic above it —
 * validation, overflow check, prefill chunking, cancel watcher, result mapping, close bookkeeping, load
 * plan — is unit-tested over a fake. Handles are opaque; `0` means the native call failed. Text crosses as
 * standard UTF-8 bytes.
 */
@Suppress("TooManyFunctions") // one function per native entry point
internal interface NativeBridge {
    /** llama_backend_init; on dynamic-backend platforms first loads the backends found in the directory. */
    fun backendInit(dynamicBackendDir: ByteArray?)

    fun backendFree()

    fun devices(): List<NativeDevice>

    /** The latest llama.cpp error line logged on this thread, if any. */
    fun lastError(): String?

    fun loadModel(path: ByteArray, nGpuLayers: Int): Long

    fun freeModel(model: Long)

    fun newContext(model: Long, nCtx: Int, nUbatch: Int, flashAttention: Int): Long

    /** The context size llama.cpp actually allocated. */
    fun contextSize(context: Long): Int

    fun freeContext(context: Long)

    fun tokenize(model: Long, text: ByteArray): IntArray

    /** `0` when the grammar does not parse. */
    fun newSampler(
        model: Long,
        grammar: ByteArray?,
        grammarRoot: ByteArray,
        lazyGrammar: Boolean,
        sampling: Sampling,
    ): Long

    fun freeSampler(sampler: Long)

    /** Empties the context's memory and clears its abort flag. */
    fun beginGeneration(context: Long)

    /** Raises the context's abort flag, read by llama.cpp's abort callback and between decode steps. */
    fun abort(context: Long)

    /** Decodes `tokens[offset, offset + count)`: `0` ok, [DECODE_ABORTED], or llama_decode's failure code. */
    fun decode(context: Long, tokens: IntArray, offset: Int, count: Int): Int

    /** Samples and decodes up to [maxTokens] tokens natively, accumulating the output bytes. */
    fun generate(context: Long, sampler: Long, maxTokens: Int): NativeGeneration

    companion object {
        const val DECODE_OK: Int = 0
        const val DECODE_ABORTED: Int = 2
    }
}

internal class NativeDevice(val name: String, val type: Int, val freeMemoryBytes: Long, val totalMemoryBytes: Long) {
    companion object {
        // ggml_backend_dev_type
        const val TYPE_CPU: Int = 0
        const val TYPE_GPU: Int = 1
        const val TYPE_IGPU: Int = 2
    }
}

/** The native generation's outcome: the whole output as UTF-8 bytes, and a `STATUS_*` code. */
internal class NativeGeneration(
    val bytes: ByteArray,
    val generatedTokens: Int,
    val status: Int,
    val decodeCode: Int = 0,
) {
    companion object {
        const val STATUS_END_OF_GENERATION: Int = 0
        const val STATUS_MAX_TOKENS: Int = 1
        const val STATUS_ABORTED: Int = 2
        const val STATUS_DECODE_FAILED: Int = 3
    }
}
