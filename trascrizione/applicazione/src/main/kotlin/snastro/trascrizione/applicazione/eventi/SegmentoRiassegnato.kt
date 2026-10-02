package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `SegmentoRiassegnato` (boundary `eventi-trascrizione-incontro`, ADR 0035
 * §5): in the Incontro [incontroId], [segmento] moved [da] → [a]; [daRimossa]: [da] left without Segmenti, [aNuova]:
 * [a] newly created.
 */
public data class SegmentoRiassegnato(
    val incontroId: IncontroId,
    val segmento: SegmentoRef,
    val da: VoceId,
    val a: VoceId,
    val daRimossa: Boolean,
    val aNuova: Boolean,
) : EventoPubblicato
