package io.github.lucolucus.llamajni

/**
 * One generation's options.
 *
 * @property maxTokens the most tokens generated, enforced natively; reaching it ends with [StopReason.MAX_TOKENS].
 * @property grammar a GBNF grammar constraining the output, or `null`.
 * @property grammarRoot the grammar's start symbol.
 * @property lazyGrammar sample without the grammar and apply it to the whole vocabulary only when it rejects
 *   the chosen token (much faster, same constraint).
 */
public data class GenerateOptions(
    public val maxTokens: Int,
    public val grammar: String?,
    public val grammarRoot: String = "root",
    public val lazyGrammar: Boolean = true,
    public val sampling: Sampling = Sampling(),
) {
    init {
        require(maxTokens > 0) { "maxTokens must be > 0, was $maxTokens" }
        require(grammarRoot.isNotBlank()) { "grammarRoot must not be blank" }
    }
}
