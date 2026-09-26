package io.github.lucolucus.llamajni

/**
 * An expected failure of the library. Deliberately NOT a [Throwable]: it is returned inside
 * [LlamaResult.Err]. Misuse (invalid parameters, a call from another thread) is a programming error and
 * fails with an exception instead.
 */
public sealed interface LlamaError {
    /** The native libraries could not be loaded from the given directory. */
    public data class NativeLoadFailed(public val detail: String) : LlamaError

    /** llama.cpp could not load the model file. */
    public data class ModelLoadFailed(public val detail: String) : LlamaError

    /** llama.cpp could not create the context for a loaded model (e.g. not enough memory). */
    public data class ContextCreateFailed(public val detail: String) : LlamaError

    /** `promptTokens + maxTokens > nCtx`: refused before any decode. */
    public data class ContextOverflow(public val promptTokens: Int, public val maxTokens: Int, public val nCtx: Int) :
        LlamaError

    /** The GBNF grammar did not parse; nothing was decoded. */
    public data class GrammarInvalid(public val detail: String) : LlamaError

    /** The caller's cancel signal stopped the generation; returned only after the native call returned. */
    public data object Cancelled : LlamaError

    /** `llama_decode` failed with [code]. */
    public data class DecodeFailed(public val code: Int) : LlamaError

    /** The model was already closed. */
    public data object Closed : LlamaError
}
