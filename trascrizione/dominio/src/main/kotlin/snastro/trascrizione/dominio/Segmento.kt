package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** Read copy of a Segmento of a [Trascritto]: [id], [intervallo] and [testo] never change (INV-8), only [voceId]. */
public data class Segmento(val id: SegmentoId, val voceId: VoceId, val intervallo: IntervalloMs, val testo: String)
