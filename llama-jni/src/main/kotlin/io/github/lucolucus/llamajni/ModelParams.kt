package io.github.lucolucus.llamajni

/**
 * How a model and its context are opened. Validated here, before any native call: an invalid value is a
 * programming error ([IllegalArgumentException]).
 *
 * @property nGpuLayers layers offloaded to the GPU: [ALL], or `0..n` (`0` = CPU only).
 * @property nCtx context size in tokens (prompt + generated).
 * @property nUbatch physical batch size of a decode.
 * @property prefillChunk tokens per prefill decode; each chunk is a cancel point. At most [nUbatch].
 */
public data class ModelParams(
    public val nGpuLayers: Int,
    public val nCtx: Int,
    public val nUbatch: Int,
    public val prefillChunk: Int = DEFAULT_PREFILL_CHUNK,
    public val flashAttention: FlashAttention = FlashAttention.AUTO,
) {
    init {
        require(nGpuLayers >= 0 || nGpuLayers == ALL) { "nGpuLayers must be ModelParams.ALL or >= 0, was $nGpuLayers" }
        require(nCtx > 0) { "nCtx must be > 0, was $nCtx" }
        require(nUbatch > 0) { "nUbatch must be > 0, was $nUbatch" }
        require(prefillChunk in 1..nUbatch) { "prefillChunk must be in 1..nUbatch ($nUbatch), was $prefillChunk" }
    }

    public companion object {
        /** Every layer on the GPU. */
        public const val ALL: Int = -1

        public const val DEFAULT_PREFILL_CHUNK: Int = 512
    }
}
