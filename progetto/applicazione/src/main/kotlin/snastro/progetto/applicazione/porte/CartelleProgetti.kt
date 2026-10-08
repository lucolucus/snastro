package snastro.progetto.applicazione.porte

/**
 * Port: whether a known project's folder is still where [RegistroProgetti] says it is (R0, EX-7/EX-12) — moved in the
 * Finder, or on an external disk that is unplugged, it is not. Asked afresh at every call: a folder that comes back
 * is found again.
 */
public interface CartelleProgetti {
    /** True if [percorso] is a folder that exists now. */
    public fun esiste(percorso: String): Boolean
}
