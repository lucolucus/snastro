package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.dominio.Impronta
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

    /** Every stored print row of a Voce of the Registrazione [id] (any Parlante). */
    public fun impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta>

    /** Every stored print row of the Parlanti of the Progetto [id]. */
    public fun impronteDelProgetto(id: ProgettoId): List<RigaImpronta>

    /**
     * Compare-and-set UPDATE of the ONE row (`attesa.parlanteId`, `attesa.voceRef`) to [impronta] /
     * [sorgente] / [modello], only if it still exists with the `sorgente` and `modello` of [attesa].
     * True iff that row was updated. NEVER inserts: a purged print is never resurrected
     * (ADR 0009/0012 Amendment (b)).
     */
    public fun aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String): Boolean
}
