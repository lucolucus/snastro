package snastro.trascrizione.dominio

import snastro.kernel.VoceId

/**
 * Outcome of [VociDellIncontro.completaParte] (INV-I5): the first transcription of a Parte, or the replacement of its
 * Segmenti. [vociNuove] are the Voci numbered from the Incontro counter for this Parte; [Sostituzione.vociRimosse]
 * are the Voci that ceased because they spoke only in the replaced Segmenti.
 */
public sealed interface ConclusioneParte {
    public val vociNuove: Set<VoceId>

    public data class PrimaTrascrizione(override val vociNuove: Set<VoceId>) : ConclusioneParte

    public data class Sostituzione(val vociRimosse: Set<VoceId>, override val vociNuove: Set<VoceId>) :
        ConclusioneParte
}
