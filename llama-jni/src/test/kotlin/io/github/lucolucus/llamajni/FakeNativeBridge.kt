package io.github.lucolucus.llamajni

import java.util.Collections

/**
 * A recording [NativeBridge]: every call is appended to [calls] (thread-safe). By default a text tokenizes to
 * one token per UTF-8 byte, every handle is valid, decodes succeed and a generation ends at end-of-generation.
 */
internal class FakeNativeBridge : NativeBridge {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val tokenized = mutableListOf<ByteArray>()
    val decodeCounts = mutableListOf<Int>()
    val samplers = mutableListOf<SamplerRequest>()
    val generateMaxTokens = mutableListOf<Int>()

    var devicesToReport: List<NativeDevice> = emptyList()
    var lastErrorText: String? = null
    var loadModelResult: Long = 0x10
    var newContextResult: Long = 0x20
    var samplerResult: Long = 0x30
    var decodeCode: (chunk: Int) -> Int = { NativeBridge.DECODE_OK }
    var generation: (maxTokens: Int) -> NativeGeneration =
        { NativeGeneration(ByteArray(0), 0, NativeGeneration.STATUS_END_OF_GENERATION) }
    private var nextModel = 0L

    class SamplerRequest(
        val grammar: ByteArray?,
        val grammarRoot: ByteArray,
        val lazyGrammar: Boolean,
        val sampling: Sampling,
    )

    fun count(call: String): Int = synchronized(calls) { calls.count { it == call } }

    override fun backendInit(dynamicBackendDir: ByteArray?) {
        calls += if (dynamicBackendDir == null) "backendInit" else "backendInit(${dynamicBackendDir.decodeToString()})"
    }

    override fun backendFree() {
        calls += "backendFree"
    }

    override fun devices(): List<NativeDevice> = devicesToReport

    override fun lastError(): String? = lastErrorText

    override fun loadModel(path: ByteArray, nGpuLayers: Int): Long {
        calls += "loadModel"
        return if (loadModelResult == 0L) 0L else loadModelResult + nextModel++
    }

    override fun freeModel(model: Long) {
        calls += "freeModel"
    }

    override fun newContext(model: Long, nCtx: Int, nUbatch: Int, flashAttention: Int): Long {
        calls += "newContext"
        return newContextResult
    }

    override fun contextSize(context: Long): Int = requestedNCtx

    var requestedNCtx: Int = 0

    override fun freeContext(context: Long) {
        calls += "freeContext"
    }

    override fun tokenize(model: Long, text: ByteArray): IntArray {
        calls += "tokenize"
        tokenized += text
        return IntArray(text.size) { it }
    }

    override fun newSampler(
        model: Long,
        grammar: ByteArray?,
        grammarRoot: ByteArray,
        lazyGrammar: Boolean,
        sampling: Sampling,
    ): Long {
        calls += "newSampler"
        samplers += SamplerRequest(grammar, grammarRoot, lazyGrammar, sampling)
        return samplerResult
    }

    override fun freeSampler(sampler: Long) {
        calls += "freeSampler"
    }

    override fun beginGeneration(context: Long) {
        calls += "beginGeneration"
    }

    override fun abort(context: Long) {
        calls += "abort"
        onAbort()
    }

    var onAbort: () -> Unit = {}

    override fun decode(context: Long, tokens: IntArray, offset: Int, count: Int): Int {
        calls += "decode"
        decodeCounts += count
        return decodeCode(count)
    }

    override fun generate(context: Long, sampler: Long, maxTokens: Int): NativeGeneration {
        calls += "generate"
        generateMaxTokens += maxTokens
        return generation(maxTokens)
    }
}
