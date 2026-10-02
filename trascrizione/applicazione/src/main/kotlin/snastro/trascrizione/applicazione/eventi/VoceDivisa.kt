package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `VoceDivisa` (boundary `eventi-trascrizione-incontro`, ADR 0035 §5): in the
 * Incontro [incontroId], [spostati] (each with its Parte) moved from [origine] to the new Voce [nuova].
 */
public data class VoceDivisa(
    val incontroId: IncontroId,
    val origine: VoceId,
    val nuova: VoceId,
    val spostati: List<SegmentoRef>,
) : EventoPubblicato
