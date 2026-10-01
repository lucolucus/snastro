package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef

/**
 * Consumer-owned, read-only port through which Sbobinatura reads the Nomi of the attributed Voci from
 * Parlanti (boundary `porte-sbobinatura`, ADR 0033 §4, ADR 0035 §7; Published Language only).
 */
public interface LettoreNomi {
    /**
     * The Nome of the Parlante each attributed Voce of the Incontro [incontroId] is attributed to, keyed by its
     * [VoceRef] (every key belongs to [incontroId]); one entry per Voce, whatever the Parte it speaks in. A Voce
     * without Attribuzione is absent (Sbobinatura renders it as "Voce n"). The Nome is the Parlante's current one, so
     * the latest rinomina wins (a change of case only included); an eliminato Parlante still resolves to the Nome it
     * had when eliminato (INV-13, INV-24). An unknown [incontroId], or one with no Attribuzione, gives an empty map.
     * It is a lookup keyed by [VoceRef]: its iteration order carries no meaning (INV-23).
     */
    public fun nomi(incontroId: IncontroId): Map<VoceRef, String>

    /**
     * Every Incontro with at least one Attribuzione to [p], each once (however many of its Voci and Parti), and no
     * other; an eliminato Parlante keeps its past Attribuzioni, so its Incontri are still listed. An unknown [p]
     * gives an empty list. No order is guaranteed.
     */
    public fun incontriCon(p: ParlanteId): List<IncontroId>
}
