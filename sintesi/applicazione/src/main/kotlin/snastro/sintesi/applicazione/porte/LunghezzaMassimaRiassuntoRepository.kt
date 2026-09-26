package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto

/**
 * Persistence port of the per-Progetto [LunghezzaMassimaRiassunto] (boundary `repo-sintesi`, ADR 0022), inside the
 * caller's transaction. Contract: `LunghezzaMassimaRiassuntoRepositoryContratto`.
 */
public interface LunghezzaMassimaRiassuntoRepository {
    /** The stored setting of [p], or [LunghezzaMassimaRiassunto.predefinita] when there is none. */
    public fun trova(p: ProgettoId): LunghezzaMassimaRiassunto

    /** Upsert of the Progetto's setting. */
    public fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit>
}
