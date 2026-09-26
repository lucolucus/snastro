package io.github.lucolucus.llamajni

/**
 * A loaded model with its context. Blocking and confined to the thread that opened it: one generation at a
 * time. A call from another thread is a programming error ([IllegalStateException]).
 */
public interface LlamaModel : AutoCloseable {
    /** The exact token count of [text] (llama_tokenize with special-token parsing, nothing added). */
    public fun countTokens(text: String): LlamaResult<Int>

    /**
     * Generates a continuation of [prompt]. The prompt is used as given (no template, nothing added); a
     * prompt with no tokens is a programming error ([IllegalArgumentException]).
     *
     * Refused with [LlamaError.ContextOverflow] before any decode when `promptTokens + maxTokens > nCtx`.
     * [cancel] is polled by a watcher thread (which then raises the native abort flag) and before every
     * prefill chunk, so it must be thread-safe; a cancelled run returns [LlamaError.Cancelled] only after the
     * native call has returned.
     */
    public fun generate(prompt: String, options: GenerateOptions, cancel: () -> Boolean): LlamaResult<Generation>

    /** Frees the context, then the model; the last open model also frees the backend. Idempotent. */
    override fun close()
}
