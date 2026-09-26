package io.github.lucolucus.llamajni

/** A completed generation: the text (decoded once from the native UTF-8 bytes), token counts, stop reason. */
public data class Generation(
    public val text: String,
    public val promptTokens: Int,
    public val generatedTokens: Int,
    public val stop: StopReason,
    public val timings: Timings,
)
