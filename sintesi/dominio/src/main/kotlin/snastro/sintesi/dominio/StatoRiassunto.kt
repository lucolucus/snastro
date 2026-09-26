package snastro.sintesi.dominio

/**
 * Lifecycle of a [Riassunto] (INV-S1): `in_attesa → in_corso → pronto | fallito`; the last two are terminal.
 * Persisted as the lowercase canonical text. Outside `dominio` and the persistence adapter never compare it:
 * use [Riassunto]'s named predicates.
 */
public enum class StatoRiassunto {
    IN_ATTESA,
    IN_CORSO,
    PRONTO,
    FALLITO,
    ;

    /** The canonical lowercase code (`in_attesa`, …). */
    public val codice: String get() = name.lowercase()
}
