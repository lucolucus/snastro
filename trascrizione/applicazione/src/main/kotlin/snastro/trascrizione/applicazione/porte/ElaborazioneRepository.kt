package snastro.trascrizione.applicazione.porte

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

    /**
     * Inserts or updates [e]. INV-4 is refused like the ADR 0007 partial unique indexes, leaving the store
     * unchanged: [ErroreApplicazioneTrascrizione.ElaborazioneGiaAperta] if another Elaborazione of the same
     * Registrazione is open while [e] is, [ErroreApplicazioneTrascrizione.ElaborazioneGiaCompletata] if another
     * one is `completata` while [e] is. Infra faults throw (ADR 0003).
     */
    public fun salva(e: Elaborazione): Esito<Unit>
}
