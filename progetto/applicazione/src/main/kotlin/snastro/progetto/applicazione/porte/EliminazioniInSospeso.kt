package snastro.progetto.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Port: the Progetto-owned table `eliminazione_in_sospeso` (5.sqm, ADR 0020 §4). A row is written in the deleting
 * transaction, so it exists iff the deletion committed, and is concluded at a later project open once the files are
 * gone. Every method runs inside the caller's transaction. Contract: `EliminazioniInSospesoContratto`.
 */
public interface EliminazioniInSospeso {
    /** Records [e]; at most one pending row per `registrazioneId`. */
    public fun registra(e: EliminazioneInSospeso)

    /** Every pending row, by registration time, then by `registrazioneId`. */
    public fun elenco(): List<EliminazioneInSospeso>

    /** Removes the pending row of [id]; an absent one is a no-op. */
    public fun concludi(id: RegistrazioneId)
}
