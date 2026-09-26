package io.github.lucolucus.llamajni

/**
 * Wall-clock times in milliseconds.
 *
 * @property loadMs opening the model and its context ([LlamaBackend.openModel]).
 * @property prefillMs decoding the prompt.
 * @property generationMs generating the output.
 */
public data class Timings(public val loadMs: Long, public val prefillMs: Long, public val generationMs: Long)
