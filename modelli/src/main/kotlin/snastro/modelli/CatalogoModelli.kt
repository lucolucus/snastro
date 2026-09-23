package snastro.modelli

/**
 * The model catalogue (ADR 0008): the [VoceCatalogo] entries `:modelli` knows how to provision.
 * Each spike ADR that chooses a model adds its entry in the real-adapter block it gates — this
 * type only holds whatever entries its caller composes it with.
 */
public data class CatalogoModelli(public val voci: List<VoceCatalogo>)
