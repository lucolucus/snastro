package io.github.lucolucus.llamajni

/** The outcome of a library call: a value, or an expected failure as a [LlamaError] (never an exception). */
public sealed interface LlamaResult<out T> {
    public data class Ok<out T>(public val value: T) : LlamaResult<T>

    public data class Err(public val error: LlamaError) : LlamaResult<Nothing>
}
