package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `SegmentoRiassegnato` (boundary `eventi-revisione`); [daRimossa]: [da] left
 * without Segmenti, [aNuova]: [a] newly created.
 */
public data class SegmentoRiassegnato(
    val registrazioneId: RegistrazioneId,
    val segmentoId: SegmentoId,
    val da: VoceId,
    val a: VoceId,
    val daRimossa: Boolean,
    val aNuova: Boolean,
) : EventoPubblicato
