package snastro.progetto.applicazione.porte

import java.time.Instant

/**
 * Port: the per-user list of known projects, keyed by [VoceRegistro.percorso] (the project folder).
 * Two folders holding the same Progetto (a copied folder) are two independent entries.
 */
public interface RegistroProgetti {
    /** Every known project, most recent [VoceRegistro.ultimaAttivita] first. */
    public fun elenco(): List<VoceRegistro>

    /** Adds [v], replacing the entry with the same [VoceRegistro.percorso] if any. */
    public fun registra(v: VoceRegistro)

    /** Updates the entry of [percorso] only; an unknown [percorso] is a no-op. */
    public fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant)

    /** Removes the entry of [percorso]; an unknown [percorso] is a no-op. */
    public fun rimuovi(percorso: String)
}
