package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Published-language view of one Segmento of a Trascritto, [testo] verbatim — ordered by inizio then
 * `segmentoId` ACROSS Voci (AC-99), the order [Trascritto.segmenti] already guarantees (ids are minted
 * once, in that order, at creation).
 */
public data class SegmentoVista(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val testo: String,
)
