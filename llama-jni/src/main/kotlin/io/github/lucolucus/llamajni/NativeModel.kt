package io.github.lucolucus.llamajni

import kotlin.math.min

/** A model and its context over a [NativeBridge], confined to the thread that opened it. */
internal class NativeModel(
    private val bridge: NativeBridge,
    private val handles: Handles,
    private val params: ModelParams,
    private val loadMs: Long,
    private val onClose: () -> Unit,
    private val nanoTime: () -> Long,
) : LlamaModel {
    /** The native model and context, and the context size llama.cpp allocated. */
    internal class Handles(val model: Long, val context: Long, val nCtx: Int)

    private val owner: Thread = Thread.currentThread()
    private var closed = false

    override fun countTokens(text: String): LlamaResult<Int> {
        confined()
        return if (closed) {
            LlamaResult.Err(LlamaError.Closed)
        } else {
            LlamaResult.Ok(bridge.tokenize(handles.model, text.encodeToByteArray()).size)
        }
    }

    override fun generate(prompt: String, options: GenerateOptions, cancel: () -> Boolean): LlamaResult<Generation> {
        confined()
        if (closed) return LlamaResult.Err(LlamaError.Closed)
        val tokens = bridge.tokenize(handles.model, prompt.encodeToByteArray())
        require(tokens.isNotEmpty()) { "the prompt has no tokens" }
        // The overflow check runs before any decode: a sum equal to nCtx still fits.
        return if (tokens.size.toLong() + options.maxTokens > handles.nCtx) {
            LlamaResult.Err(LlamaError.ContextOverflow(tokens.size, options.maxTokens, handles.nCtx))
        } else {
            withSampler(tokens, options, cancel)
        }
    }

    private fun withSampler(
        tokens: IntArray,
        options: GenerateOptions,
        cancel: () -> Boolean,
    ): LlamaResult<Generation> {
        val sampler = bridge.newSampler(
            handles.model,
            options.grammar?.encodeToByteArray(),
            options.grammarRoot.encodeToByteArray(),
            options.lazyGrammar,
            options.sampling,
        )
        if (sampler == 0L) {
            val detail = bridge.lastError() ?: "the grammar does not parse (root '${options.grammarRoot}')"
            return LlamaResult.Err(LlamaError.GrammarInvalid(detail))
        }
        try {
            return if (cancel()) {
                LlamaResult.Err(LlamaError.Cancelled)
            } else {
                bridge.beginGeneration(handles.context)
                CancelWatcher(cancel) { bridge.abort(handles.context) }.use {
                    run(tokens, sampler, options.maxTokens, cancel)
                }
            }
        } finally {
            bridge.freeSampler(sampler)
        }
    }

    private fun run(tokens: IntArray, sampler: Long, maxTokens: Int, cancel: () -> Boolean): LlamaResult<Generation> {
        val prefillStart = nanoTime()
        val prefillError = prefill(tokens, cancel)
        if (prefillError != null) return LlamaResult.Err(prefillError)
        val generationStart = nanoTime()
        val out = bridge.generate(handles.context, sampler, maxTokens)
        val end = nanoTime()
        fun completed(stop: StopReason): LlamaResult<Generation> {
            val timings = Timings(
                loadMs = loadMs,
                prefillMs = (generationStart - prefillStart) / NANOS_PER_MILLI,
                generationMs = (end - generationStart) / NANOS_PER_MILLI,
            )
            // Decoded once, from all the bytes: a token may end in the middle of a multi-byte character.
            val text = out.bytes.decodeToString()
            return LlamaResult.Ok(Generation(text, tokens.size, out.generatedTokens, stop, timings))
        }
        return when (out.status) {
            NativeGeneration.STATUS_END_OF_GENERATION -> completed(StopReason.END_OF_GENERATION)
            NativeGeneration.STATUS_MAX_TOKENS -> completed(StopReason.MAX_TOKENS)
            NativeGeneration.STATUS_ABORTED -> LlamaResult.Err(LlamaError.Cancelled)
            else -> LlamaResult.Err(LlamaError.DecodeFailed(out.decodeCode))
        }
    }

    /** The prompt in [ModelParams.prefillChunk]-token decodes, each a cancel point; `null` when all decoded. */
    private fun prefill(tokens: IntArray, cancel: () -> Boolean): LlamaError? {
        var offset = 0
        var error: LlamaError? = null
        while (error == null && offset < tokens.size) {
            val count = min(params.prefillChunk, tokens.size - offset)
            error = if (cancel()) {
                LlamaError.Cancelled
            } else {
                when (val code = bridge.decode(handles.context, tokens, offset, count)) {
                    NativeBridge.DECODE_OK -> null
                    NativeBridge.DECODE_ABORTED -> LlamaError.Cancelled
                    else -> LlamaError.DecodeFailed(code)
                }
            }
            offset += count
        }
        return error
    }

    override fun close() {
        confined()
        if (closed) return
        closed = true
        bridge.freeContext(handles.context)
        bridge.freeModel(handles.model)
        onClose()
    }

    private fun confined() = check(Thread.currentThread() === owner) {
        "LlamaModel is confined to thread '${owner.name}', called from '${Thread.currentThread().name}'"
    }
}
