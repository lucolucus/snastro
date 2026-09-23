package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import java.time.Instant

/** Port: the per-user list of known projects, keyed by [VoceRegistro.percorso]. */
public interface RegistroProgetti {
    /** Every known project, most recent [VoceRegistro.ultimaAttivita] first. */
    public fun elenco(): List<VoceRegistro>

    /** Adds [v], replacing the entry with the same [VoceRegistro.percorso] if any. */
    public fun registra(v: VoceRegistro)

    public fun aggiorna(progettoId: ProgettoId, numRegistrazioni: Int, ultimaAttivita: Instant)

    public fun rimuovi(percorso: String)
}
