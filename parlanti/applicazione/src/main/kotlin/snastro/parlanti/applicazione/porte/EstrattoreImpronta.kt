package snastro.parlanti.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.parlanti.dominio.Impronta

/**
 * Speaker-print extraction (boundary `tec-estrattore-impronta`, ADR 0004/0009): deterministic, constant
 * dimension; native use serialized with the pipeline (ADR 0012). Contract: `EstrattoreImprontaContratto`.
 */
public interface EstrattoreImpronta {
    public fun estrai(c: CampioniAudio): Impronta
}
