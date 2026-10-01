package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.VoceRef

/**
 * Sintesi's own consumer-owned, read-only port on the current Nomi of the Voci (boundary `porte-sintesi`,
 * Parlanti → Sintesi, ADR 0033 §4; Published Language only).
 *
 * Names are read at display only and are NEVER stored nor shown to the model (INV-S5, ADR 0032). Whether a Voce is
 * still present is NOT asked here: Sintesi derives it from the structure it reads (ADR 0037 §6).
 */
public interface LettoreNomi {
    /**
     * The current Nome of the Parlante each attributed Voce of the Incontro [incontroId] is attributed to, whatever
     * its Parte, keyed by its [VoceRef] (every key's `incontroId` equals [incontroId]). A Voce without Attribuzione
     * has no key (Sintesi renders it as "Voce n"); an eliminato Parlante still resolves to its Nome. An unknown
     * Incontro gives an empty map. Iteration order carries no meaning.
     */
    public fun nomi(incontroId: IncontroId): Map<VoceRef, String>
}
