package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Published-language view of one Segmento for Parlanti's `LettoreVoci.segmenti` (boundary `voci-per-parlanti`,
 * ADR 0019 §4.1): its Voce, [intervallo] and [confermato] flag as stored — NEVER its text (AC-550, by type).
 */
public data class SegmentoDiVoceVista(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val confermato: Boolean,
)
