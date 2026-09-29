package snastro.sintesi.dominio

/**
 * Estimate of the labelled input's size (ADR 0021 §5, calibrated by ADR 0026 §5): tokens =
 * ⌈characters × [TOKEN_PER_CARATTERI_NUM] ÷ [TOKEN_PER_CARATTERI_DEN]⌉ (≡ ⌈characters ÷ 2.4⌉, integer arithmetic),
 * within the limit iff ≤ [LIMITE_TOKEN]. The constants' single home.
 *
 * [LIMITE_TOKEN] is the model's own ceiling, not a product cap (2026-09-29, amending ADR 0026 §5): Qwen3.5 9B's
 * native context 262 144 − ≤ 512 template − 35 512 `max_tokens` at the 10 000-word cap = 226 120, rounded down.
 * 540 000 characters ≈ 9–11 h of audio; the runtime sizes its context to each input.
 */
public object LimiteIngresso {
    public const val TOKEN_PER_CARATTERI_NUM: Int = 5
    public const val TOKEN_PER_CARATTERI_DEN: Int = 12
    public const val LIMITE_TOKEN: Int = 225_000

    public fun stimaToken(ingresso: String): Int = stimaTokenDiLunghezza(ingresso.length)

    /**
     * A59: Long arithmetic — `caratteri * 5` overflows Int above ~429M chars, which would otherwise wrap to a
     * negative estimate and let `valuta()` pass it (`contaToken` is the real backstop, but the estimate must
     * never lie). Coerced into Int range, well above [LIMITE_TOKEN] either way. Split out (`internal`) so the
     * overflow boundary is tested on the length alone, without allocating a ~430 MB string.
     */
    internal fun stimaTokenDiLunghezza(caratteri: Int): Int {
        val token = (TOKEN_PER_CARATTERI_NUM.toLong() * caratteri + TOKEN_PER_CARATTERI_DEN - 1) /
            TOKEN_PER_CARATTERI_DEN
        return token.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
