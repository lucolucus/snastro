package snastro.trascrizione.dominio

import snastro.kernel.VoceId

/** Read copy of a Voce of a [Trascritto]: never empty (INV-6), [segmenti] ordered by inizio then id (INV-7). */
public data class Voce(val id: VoceId, val segmenti: List<Segmento>)
