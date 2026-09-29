package snastro.sintesi.adattatori.ml

/**
 * The bounded GBNF of answer schema v1 (`risposta-v1.gbnf` next to this class, ADR 0026 §5): every list ≤ N items,
 * every `fonti` ≤ 6 ids, ids `[1-9][0-9]{0,5}`. Handed to EVERY generation, with `max_tokens` (the second bound).
 * N is per request ([MisuraRisposta.massimoVoci], 2026-09-30): the resource holds the `{0,@ALTRE_VOCI@}` slot.
 */
internal object GrammaticaRisposta {
    private const val SEGNAPOSTO = "@ALTRE_VOCI@"

    private val MODELLO: String = checkNotNull(GrammaticaRisposta::class.java.getResource("risposta-v1.gbnf")) {
        "risorsa risposta-v1.gbnf assente dal classpath di :sintesi:adattatori"
    }.readText()

    /** The grammar whose three lists hold at most [massimoVoci] items each. */
    fun per(massimoVoci: Int): String {
        require(massimoVoci >= 1) { "massimoVoci deve essere almeno 1: $massimoVoci" }
        return MODELLO.replace(SEGNAPOSTO, (massimoVoci - 1).toString())
    }
}
