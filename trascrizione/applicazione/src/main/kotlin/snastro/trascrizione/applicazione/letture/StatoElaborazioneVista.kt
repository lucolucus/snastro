package snastro.trascrizione.applicazione.letture

/**
 * Published-language state of a Registrazione's processing, as [StatiElaborazione] exposes it: the four
 * [snastro.trascrizione.dominio.StatoElaborazione] values of its latest `Elaborazione`, plus
 * [NON_AVVIATA] for a Registrazione with no `Elaborazione` at all (AC-162). Never built by comparing the
 * domain enum outside `dominio`/the persistence adapter (§14 gate): [StatiElaborazione] derives it only
 * from `Elaborazione`'s named predicates (`completata`, `fallita`) and `avviataAlle`.
 */
public enum class StatoElaborazioneVista {
    NON_AVVIATA,
    IN_ATTESA,
    IN_CORSO,
    COMPLETATA,
    FALLITA,
}
