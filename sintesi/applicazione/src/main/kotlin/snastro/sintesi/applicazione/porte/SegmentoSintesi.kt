package snastro.sintesi.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * One Segmento of a Trascritto as Sintesi reads it (Published Language: kernel ids, verbatim [testo]).
 * [segmentoId] and [voceId] are valid for the Trascritto generation they were read from (ADR 0018).
 */
public data class SegmentoSintesi(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val testo: String,
)
