package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `VociUnite` (boundary `eventi-trascrizione-incontro`, ADR 0035 §5): in the
 * Incontro [incontroId], [rimossa] merged into [sopravvissuta], in every Parte.
 */
public data class VociUnite(
    val incontroId: IncontroId,
    val sopravvissuta: VoceId,
    val rimossa: VoceId,
) : EventoPubblicato
