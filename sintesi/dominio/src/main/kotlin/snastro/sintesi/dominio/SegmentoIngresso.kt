package snastro.sintesi.dominio

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/** One Segmento of the Parte [registrazioneId] as [IngressoRiassunto] labels it; [voceId] is its Incontro Voce. */
public data class SegmentoIngresso(
    val registrazioneId: RegistrazioneId,
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val inizioMs: Long,
    val testo: String,
) {
    internal val ref: SegmentoRef get() = SegmentoRef(registrazioneId, segmentoId)
}
