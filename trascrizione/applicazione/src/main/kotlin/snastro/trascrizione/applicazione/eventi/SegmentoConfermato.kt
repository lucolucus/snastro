package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoRef

/**
 * Published Language of the domain event `SegmentoConfermato` (boundary `eventi-trascrizione-incontro`, ADR 0019 §3,
 * ADR 0035 §5): in the Incontro [incontroId], the [confermato] flag of [segmento] changed. After-commit subscribers
 * only (view refresh); no synchronous one.
 */
public data class SegmentoConfermato(
    val incontroId: IncontroId,
    val segmento: SegmentoRef,
    val confermato: Boolean,
) : EventoPubblicato
