package snastro.documento.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * One Segmento of a [TrascrittoTesto] (pinned view of `trascritto-per-documento`): the Voce it
 * belongs to now (after any Revisione), its time [intervallo] and its [testo] verbatim.
 */
public data class SegmentoVista(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val testo: String,
)
