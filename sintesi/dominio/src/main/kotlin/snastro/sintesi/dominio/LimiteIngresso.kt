package snastro.sintesi.dominio

/**
 * Provisional estimate of the labelled input's size (ADR 0021 §5, until spike runtime-llm-in-app): tokens =
 * ⌈characters ÷ [CARATTERI_PER_TOKEN]⌉, within the limit iff ≤ [LIMITE_TOKEN]. The constants' single home.
 */
public object LimiteIngresso {
    public const val CARATTERI_PER_TOKEN: Int = 3
    public const val LIMITE_TOKEN: Int = 28_000

    public fun stimaToken(ingresso: String): Int = (ingresso.length + CARATTERI_PER_TOKEN - 1) / CARATTERI_PER_TOKEN
}
