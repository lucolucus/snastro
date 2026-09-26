package snastro.sintesi.applicazione.letture

/** AC-S104: one Fonte, with the CURRENT speaker and start time of its Segmento. */
public data class FonteVista(val segmentoId: Int, val voce: VoceVista, val inizioMs: Long)
