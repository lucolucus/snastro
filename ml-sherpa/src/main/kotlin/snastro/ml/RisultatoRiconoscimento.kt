package snastro.ml

import snastro.kernel.IntervalloMs

/** One recognized token; [intervallo] is relative to the start of the decoded samples. */
data class TokenRiconosciuto(val testo: String, val intervallo: IntervalloMs)

/** What one decode call returned. [token] is `null` when the model gave no timestamps. */
data class RisultatoRiconoscimento(val testo: String, val token: List<TokenRiconosciuto>?)
