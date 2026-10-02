package snastro.parlanti.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/**
 * One current Segmento as read through [LettoreVoci.segmenti], addressed by its [segmento] (Parte + segmentoId) and
 * its Voce of the Incontro [voceId]: NEVER the text (ADR 0019 §4.1). [confermato] is exactly `Segmento.confermato`
 * as stored (Trascrizione, [INV-26]) — this port neither derives nor interprets it.
 */
public data class SegmentoDiVoce(
    val segmento: SegmentoRef,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val confermato: Boolean,
)
