package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** One Segmento as [IngressoRiassunto] labels it. */
public data class SegmentoIngresso(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val inizioMs: Long,
    val testo: String,
)
