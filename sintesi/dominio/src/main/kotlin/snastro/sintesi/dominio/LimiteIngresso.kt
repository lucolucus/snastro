package snastro.sintesi.dominio

/**
 * Estimate of the labelled input's size (ADR 0021 §5, calibrated by ADR 0026 §5): tokens =
 * ⌈characters × [TOKEN_PER_CARATTERI_NUM] ÷ [TOKEN_PER_CARATTERI_DEN]⌉ (≡ ⌈characters ÷ 2.4⌉, integer arithmetic),
 * within the limit iff ≤ [LIMITE_TOKEN]. The constants' single home.
 */
public object LimiteIngresso {
    public const val TOKEN_PER_CARATTERI_NUM: Int = 5
    public const val TOKEN_PER_CARATTERI_DEN: Int = 12
    public const val LIMITE_TOKEN: Int = 28_000

    public fun stimaToken(ingresso: String): Int =
        (TOKEN_PER_CARATTERI_NUM * ingresso.length + TOKEN_PER_CARATTERI_DEN - 1) / TOKEN_PER_CARATTERI_DEN
}
