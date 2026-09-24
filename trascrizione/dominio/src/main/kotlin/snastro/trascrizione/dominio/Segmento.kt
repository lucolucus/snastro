package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Read copy of a Segmento of a [Trascritto]: [id], [intervallo] and [testo] never change (INV-8), only [voceId]
 * and [confermato]. [confermato] is true iff an explicit user act placed or confirmed the Segmento on its current
 * Voce and no `ConfermaSegmento(false)` revoked it since (INV-26); [Trascritto.crea] starts it at false.
 */
public data class Segmento(
    val id: SegmentoId,
    val voceId: VoceId,
    val intervallo: IntervalloMs,
    val testo: String,
    val confermato: Boolean = false,
)
