package snastro.sintesi.adattatori.ml

/**
 * The bounded GBNF of the answer (`risposta-sommario.gbnf` next to this class, ADR 0026 §5): `{"sommario": "…"}` and
 * nothing else (R1-8, 2026-10-10: in spike H1 only the Sommario written alone kept the key points without inventing
 * any). Handed to EVERY generation, with `max_tokens` (the second bound).
 */
internal object GrammaticaRisposta {
    val GBNF: String = checkNotNull(GrammaticaRisposta::class.java.getResource("risposta-sommario.gbnf")) {
        "risorsa risposta-sommario.gbnf assente dal classpath di :sintesi:adattatori"
    }.readText()
}
