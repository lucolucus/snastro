package snastro.documento.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * Consumer-owned, read-only port through which Documento reads the Nomi of the attributed Voci from
 * Parlanti (boundary `nomi-per-documento`, Customer/Supplier, Published Language only).
 */
public interface LettoreNomi {
    /**
     * The Nome of the Parlante each attributed Voce of [id] is attributed to, keyed by its [VoceRef]
     * (every key belongs to [id]). A Voce without Attribuzione is absent (Documento renders it as
     * "Voce n"). The Nome is the Parlante's current one, so the latest rinomina wins (a change of
     * case only included); an eliminato Parlante still resolves to the Nome it had when eliminato
     * (INV-13, INV-24). An unknown [id], or one with no Attribuzione, gives an empty map.
     * It is a lookup keyed by [VoceRef]: its iteration order carries no meaning (INV-23).
     */
    public fun nomi(id: RegistrazioneId): Map<VoceRef, String>

    /**
     * Every Registrazione with at least one Attribuzione to [p], each once, and no other; an
     * eliminato Parlante keeps its past Attribuzioni, so its Registrazioni are still listed. An
     * unknown [p] gives an empty list. No order is guaranteed.
     */
    public fun registrazioniCon(p: ParlanteId): List<RegistrazioneId>
}
