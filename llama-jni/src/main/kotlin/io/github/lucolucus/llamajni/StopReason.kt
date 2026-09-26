package io.github.lucolucus.llamajni

/** Why a generation ended. */
public enum class StopReason {
    /** The model produced an end-of-generation token. */
    END_OF_GENERATION,

    /** [GenerateOptions.maxTokens] was reached. */
    MAX_TOKENS,
}
