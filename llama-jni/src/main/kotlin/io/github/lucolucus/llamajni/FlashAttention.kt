package io.github.lucolucus.llamajni

/** When llama.cpp uses flash attention. [AUTO] lets llama.cpp decide per device. */
public enum class FlashAttention(internal val nativeCode: Int) {
    AUTO(-1),
    ON(1),
    OFF(0),
}
