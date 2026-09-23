package snastro.trascrizione.dominio

/**
 * Lifecycle of an [Elaborazione] (INV-3): `in_attesa → in_corso → completata | fallita`; the last two
 * are terminal. Persisted as the lowercase canonical text. Outside `dominio` and the persistence
 * adapter never compare it: use [Elaborazione]'s named predicates.
 */
public enum class StatoElaborazione {
    IN_ATTESA,
    IN_CORSO,
    COMPLETATA,
    FALLITA,
}
