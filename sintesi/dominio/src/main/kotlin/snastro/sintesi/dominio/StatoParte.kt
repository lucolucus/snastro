package snastro.sintesi.dominio

/**
 * The state of one Parte as [Riassumibilita] reads it (INV-I9, ADR 0033 §4: the four cases of `statoParte`, D-0020).
 * Only [TRASCRITTA] lets a Riassunto be requested.
 */
public enum class StatoParte {
    /** No Trascritto and no run. */
    DA_TRASCRIVERE,

    /** A run is open (in_attesa or in_corso), with or without a previous Trascritto. */
    IN_TRASCRIZIONE,

    /** No Trascritto, and the latest run failed. */
    NON_RIUSCITA,

    /** A Trascritto, and no run open. */
    TRASCRITTA,
}
