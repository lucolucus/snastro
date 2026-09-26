package io.github.lucolucus.llamajni

/**
 * Sampler settings, applied in this order: top-k, top-p, temperature, then a seeded random draw.
 *
 * @property temperature `0` is greedy; must be >= 0.
 * @property topK keep the k most likely tokens; `0` disables it.
 * @property topP nucleus sampling threshold, in `(0, 1]` (`1` disables it).
 * @property seed the random seed; [RANDOM_SEED] draws a new one per generation.
 */
public data class Sampling(
    public val temperature: Float = DEFAULT_TEMPERATURE,
    public val topK: Int = DEFAULT_TOP_K,
    public val topP: Float = DEFAULT_TOP_P,
    public val seed: Int = RANDOM_SEED,
) {
    init {
        require(temperature >= 0f) { "temperature must be >= 0, was $temperature" }
        require(topK >= 0) { "topK must be >= 0, was $topK" }
        require(topP > 0f && topP <= 1f) { "topP must be in (0, 1], was $topP" }
    }

    public companion object {
        public const val DEFAULT_TEMPERATURE: Float = 0.8f
        public const val DEFAULT_TOP_K: Int = 40
        public const val DEFAULT_TOP_P: Float = 0.95f

        /** llama.cpp's LLAMA_DEFAULT_SEED (0xFFFFFFFF): a random seed. */
        public const val RANDOM_SEED: Int = -1
    }
}
