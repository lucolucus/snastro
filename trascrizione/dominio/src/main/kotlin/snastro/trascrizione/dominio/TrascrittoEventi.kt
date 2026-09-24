package snastro.trascrizione.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

public data class TrascrittoCreato(val registrazioneId: RegistrazioneId) : EventoDominio

/** INV-9: every Segmento of [rimossa] moved to [sopravvissuta]; [rimossa] no longer exists. */
public data class VociUnite(
    val registrazioneId: RegistrazioneId,
    val sopravvissuta: VoceId,
    val rimossa: VoceId,
) : EventoDominio

/** INV-10: [segmentiSpostati] (in the new Voce's INV-7 order) left [origine] and form [nuova]. */
public data class VoceDivisa(
    val registrazioneId: RegistrazioneId,
    val origine: VoceId,
    val nuova: VoceId,
    val segmentiSpostati: List<SegmentoId>,
) : EventoDominio

/** INV-11: [segmentoId] moved [da] → [a]; [daRimossa] if [da] was emptied, [aNuova] if [a] was created. */
public data class SegmentoRiassegnato(
    val registrazioneId: RegistrazioneId,
    val segmentoId: SegmentoId,
    val da: VoceId,
    val a: VoceId,
    val daRimossa: Boolean,
    val aNuova: Boolean,
) : EventoDominio

/** INV-26: the [confermato] flag of [segmentoId] changed (ADR 0019 §3). */
public data class SegmentoConfermato(
    val registrazioneId: RegistrazioneId,
    val segmentoId: SegmentoId,
    val confermato: Boolean,
) : EventoDominio
