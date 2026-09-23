package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `VoceDivisa` (boundary `eventi-revisione`): [segmentiSpostati] moved from
 * [origine] to [nuova].
 */
public data class VoceDivisa(
    val registrazioneId: RegistrazioneId,
    val origine: VoceId,
    val nuova: VoceId,
    val segmentiSpostati: List<SegmentoId>,
) : EventoPubblicato
