package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.parlanti.dominio.Impronta

/**
 * Speaker-print extraction (boundary `tec-estrattore-impronta`, ADR 0004/0009): deterministic, constant
 * dimension. NEVER called while a `UnitaDiLavoro` transaction is open; the adapter takes the native Mutex
 * INSIDE [estrai] (ADR 0012 Amendment (b) points 2, 5). Contract: `EstrattoreImprontaContratto`.
 */
public interface EstrattoreImpronta {
    /** Catalogue id of the embedding model (ADR 0008), stored as `impronta_vocale.modello_impronta`. */
    public val modello: String

    public fun estrai(c: CampioniAudio): Impronta
}
