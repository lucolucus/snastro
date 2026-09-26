package io.github.lucolucus.llamajni

/** The real [NativeBridge]: the shim `libllamajni` (src/main/c/llamajni.c). Usable only after it is loaded. */
@Suppress("TooManyFunctions") // one override + one external per native entry point
internal object JniBridge : NativeBridge {
    override fun backendInit(dynamicBackendDir: ByteArray?) = nBackendInit(dynamicBackendDir)

    override fun backendFree() = nBackendFree()

    override fun devices(): List<NativeDevice> = (0 until nDeviceCount()).map { i ->
        val memory = LongArray(2)
        nDeviceMemory(i, memory)
        NativeDevice(nDeviceName(i).decodeToString(), nDeviceKind(i), memory[0], memory[1])
    }

    override fun lastError(): String? = nLastError()?.decodeToString()

    override fun loadModel(path: ByteArray, nGpuLayers: Int): Long = nLoadModel(path, nGpuLayers)

    override fun freeModel(model: Long) = nFreeModel(model)

    override fun newContext(model: Long, nCtx: Int, nUbatch: Int, flashAttention: Int): Long =
        nNewContext(model, nCtx, nUbatch, flashAttention)

    override fun contextSize(context: Long): Int = nContextSize(context)

    override fun freeContext(context: Long) = nFreeContext(context)

    override fun tokenize(model: Long, text: ByteArray): IntArray =
        checkNotNull(nTokenize(model, text)) { "llama_tokenize failed (out of memory or int32 overflow)" }

    override fun newSampler(
        model: Long,
        grammar: ByteArray?,
        grammarRoot: ByteArray,
        lazyGrammar: Boolean,
        sampling: Sampling,
    ): Long = nNewSampler(
        model,
        grammar,
        grammarRoot,
        lazyGrammar,
        sampling.temperature,
        sampling.topK,
        sampling.topP,
        sampling.seed,
    )

    override fun freeSampler(sampler: Long) = nFreeSampler(sampler)

    override fun beginGeneration(context: Long) = nBeginGeneration(context)

    override fun abort(context: Long) = nAbort(context)

    override fun decode(context: Long, tokens: IntArray, offset: Int, count: Int): Int =
        nDecode(context, tokens, offset, count)

    override fun generate(context: Long, sampler: Long, maxTokens: Int): NativeGeneration {
        val out = IntArray(GENERATE_OUT_SIZE)
        val bytes = nGenerate(context, sampler, maxTokens, out)
        return NativeGeneration(bytes, generatedTokens = out[0], status = out[1], decodeCode = out[2])
    }

    /** nGenerate's out-array: generated tokens, status, decode code. */
    private const val GENERATE_OUT_SIZE = 3

    @JvmStatic private external fun nBackendInit(dynamicBackendDir: ByteArray?)

    @JvmStatic private external fun nBackendFree()

    @JvmStatic private external fun nDeviceCount(): Int

    @JvmStatic private external fun nDeviceName(index: Int): ByteArray

    @JvmStatic private external fun nDeviceKind(index: Int): Int

    @JvmStatic private external fun nDeviceMemory(index: Int, out: LongArray)

    @JvmStatic private external fun nLastError(): ByteArray?

    @JvmStatic private external fun nLoadModel(path: ByteArray, nGpuLayers: Int): Long

    @JvmStatic private external fun nFreeModel(model: Long)

    @JvmStatic private external fun nNewContext(model: Long, nCtx: Int, nUbatch: Int, flashAttention: Int): Long

    @JvmStatic private external fun nContextSize(context: Long): Int

    @JvmStatic private external fun nFreeContext(context: Long)

    @JvmStatic private external fun nTokenize(model: Long, text: ByteArray): IntArray?

    @Suppress("LongParameterList") // the JNI entry point takes the sampler settings as primitives
    @JvmStatic
    private external fun nNewSampler(
        model: Long,
        grammar: ByteArray?,
        grammarRoot: ByteArray,
        lazyGrammar: Boolean,
        temperature: Float,
        topK: Int,
        topP: Float,
        seed: Int,
    ): Long

    @JvmStatic private external fun nFreeSampler(sampler: Long)

    @JvmStatic private external fun nBeginGeneration(context: Long)

    @JvmStatic private external fun nAbort(context: Long)

    @JvmStatic private external fun nDecode(context: Long, tokens: IntArray, offset: Int, count: Int): Int

    @JvmStatic private external fun nGenerate(context: Long, sampler: Long, maxTokens: Int, out: IntArray): ByteArray
}
