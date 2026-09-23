package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante

/**
 * Repository port of the [Parlante] aggregate (boundary `repo-parlanti`, ADR 0006/0007/0009). Runs inside
 * the caller's transaction, never opens one. Contract: `ParlanteRepositoryContratto`.
 */
public interface ParlanteRepository {
    public fun trova(id: ParlanteId): Parlante?

    /** Every Parlante of the Progetto, `attivo` and `eliminato`; no order guaranteed. */
    public fun delProgetto(id: ProgettoId): List<Parlante>

    /** [INV-16] pre-check: an `attivo` Parlante of [progettoId], other than [escluso], has [nome]'s normalized form. */
    public fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean

    /**
     * Upsert of the root + replacement of its prints. [INV-16] backstop of the unique index:
     * `Errore(ErroreParlanti.NomeGiaInUso)` and nothing stored.
     */
    public fun salva(p: Parlante): Esito<Unit>

    /** Physical removal with its prints — ONLY for the INV-25 cessation of an `occasionale`. */
    public fun rimuovi(id: ParlanteId)
}
