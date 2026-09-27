package snastro.sintesi.adattatori.ml

/**
 * The bounded GBNF of answer schema v1 (`risposta-v1.gbnf` next to this class, ADR 0026 §5): every list ≤ 6 items,
 * every `fonti` ≤ 6 ids, ids `[1-9][0-9]{0,5}`. Handed to EVERY generation, with `max_tokens` (the second bound).
 */
internal object GrammaticaRisposta {
    val TESTO: String = checkNotNull(GrammaticaRisposta::class.java.getResource("risposta-v1.gbnf")) {
        "risorsa risposta-v1.gbnf assente dal classpath di :sintesi:adattatori"
    }.readText()
}
