package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId

/**
 * Persistence port of the [Riassunto] aggregate (boundary `repo-sintesi`, ADR 0022). Every write runs inside the
 * caller's `UnitaDiLavoro` transaction, never opens one. Contract: `RiassuntoRepositoryContratto`.
 *
 * Method set closed by ADR 0037 (allow-list check); a new method is an ADR amendment.
 */
public interface RiassuntoRepository {
    public fun trova(id: RiassuntoId): Riassunto?

    /**
     * The Riassunti of the Incontro [incontroId] (ADR 0037 §1): its open or `fallito` one and its `pronto` one, at most
     * one each (INV-S2, INV-S3), ordered by (richiestoAlle, id); empty for none.
     */
    public fun trova(incontroId: IncontroId): List<Riassunto>

    /** The `in_attesa` ones, FIFO by (richiestoAlle, id). */
    public fun inAttesa(): List<Riassunto>

    public fun inCorso(): List<Riassunto>

    /**
     * Upsert of the root + replace of its elements and Fonti. A second `in_attesa | in_corso | fallito` of the same
     * Incontro (index `riassunto_non_pronto_unico`) or a second `pronto` of it (index `riassunto_pronto_unico`)
     * → `Errore(RiassuntoGiaAperto(incontroId))`; nothing is written on an `Errore`. Any other constraint failure
     * is an infrastructure fault (ADR 0003), never an `Errore` (D-0003).
     */
    public fun salva(r: Riassunto): Esito<Unit>

    /**
     * The completion compare-and-set (INV-S8, ADR 0022 §4) of [r], now `pronto` or `fallito`, in ONE call: it first
     * re-reads the stored row; absent or no longer `in_corso` → `Ok(false)` and nothing is written (a previous
     * `pronto` of the Registrazione included). Only after that check, and only when [r] is `pronto`, the same call
     * removes the previous `pronto` of the Incontro (INV-S3; callers never remove it first), then writes [r]'s
     * state and children where the row is still `in_corso` → `Ok(true)` (D-0003).
     */
    public fun concludi(r: Riassunto): Esito<Boolean>

    /** Removes the Riassunto with its elements and Fonti; an absent id is a no-op. */
    public fun rimuovi(id: RiassuntoId): Esito<Unit>

    /**
     * Removes every Riassunto of the Incontro [incontroId] (elements and Fonti included); the number removed, 0 on
     * none.
     */
    public fun rimuoviDiIncontro(incontroId: IncontroId): Esito<Int>
}
