package snastro.trascrizione.applicazione.letture

/** The state of one Parte as [StatiElaborazione.statoParte] answers it (ADR 0033 §4): an open run wins. */
public enum class StatoParte { DA_TRASCRIVERE, IN_TRASCRIZIONE, NON_RIUSCITA, TRASCRITTA }
