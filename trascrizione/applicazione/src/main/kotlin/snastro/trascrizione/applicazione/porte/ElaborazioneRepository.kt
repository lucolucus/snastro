package snastro.trascrizione.applicazione.porte

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.dominio.Elaborazione

/**
 * Repository port of [Elaborazione] (boundary `repo-trascrizione`, ADR 0006/0007). Every read returns
 * copies: a caller never aliases the stored state. Contract: `ElaborazioneRepositoryContratto`.
 */
public interface ElaborazioneRepository {
    /** Every Elaborazione of the Registrazione [id], in no guaranteed order; empty if none. */
    public fun diRegistrazione(id: RegistrazioneId): List<Elaborazione>

    /** Every `in_attesa` Elaborazione, FIFO: by `creataAlle`, ties by id. */
    public fun inAttesa(): List<Elaborazione>

    /** Every `in_corso` Elaborazione, in no guaranteed order. */
    public fun inCorso(): List<Elaborazione>

    /** The Elaborazione [id], or `null` if there is none (e.g. cancelled). */
    public fun trova(id: ElaborazioneId): Elaborazione?

    /**
     * Inserts or updates [e]. INV-4 is refused like the ADR 0007 partial unique index `elaborazione_aperta_unica`,
     * leaving the store unchanged, with `ErroreTrascrizione.ElaborazioneGiaAperta` (`:trascrizione:dominio`) if
     * another Elaborazione of the same Registrazione is open while [e] is — its only refusal: several `completata`
     * are allowed (ADR 0018). Infra faults throw (ADR 0003).
     */
    public fun salva(e: Elaborazione): Esito<Unit>

    /**
     * Compare-and-delete (ADR 0018 Amendment (b)), the only deletion of an Elaborazione (amended by ADR 0020: plus
     * [rimuoviDiRegistrazione]): deletes [id] iff it
     * exists and is still `in_attesa`, judged by the store at delete time (never by an earlier read). A started
     * one → `ErroreTrascrizione.ElaborazioneGiaAvviata`, an absent one → `ErroreTrascrizione.ElaborazioneNonTrovata`,
     * the store unchanged either way. Infra faults throw (ADR 0003).
     */
    public fun rimuoviInAttesa(id: ElaborazioneId): Esito<Unit>

    /**
     * Deletes EVERY Elaborazione of the Registrazione [id], any state, and none of another — inside the caller's
     * transaction. Used only by the elimination policy after its veto (ADR 0020). Infra faults throw (ADR 0003).
     */
    public fun rimuoviDiRegistrazione(id: RegistrazioneId)
}
