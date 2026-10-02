package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * Published-language view of one Segmento of an Incontro, by [SegmentoRef], with its Incontro [voceId], [intervallo]
 * and [confermato] flag as stored — NEVER its text (by type).
 */
public data class SegmentoDiVoceIncontro(
    val segmento: SegmentoRef,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val confermato: Boolean,
)
