package snastro.progetto.applicazione.porte

/** In-memory [CartelleProgetti]: every folder exists, but those [sposta]ta away (until [rimetti]). */
public class CartelleProgettiFinta : CartelleProgetti {
    private val mancanti = mutableSetOf<String>()

    override fun esiste(percorso: String): Boolean = percorso !in mancanti

    /** The folder [percorso] is moved away: it no longer exists. */
    public fun sposta(percorso: String) {
        mancanti += percorso
    }

    /** The folder [percorso] is back where it was. */
    public fun rimetti(percorso: String) {
        mancanti -= percorso
    }
}
