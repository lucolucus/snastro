package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * Published Language of the domain event `VociUnite` (boundary `eventi-revisione`): [rimossa] merged into
 * [sopravvissuta].
 */
public data class VociUnite(
    val registrazioneId: RegistrazioneId,
    val sopravvissuta: VoceId,
    val rimossa: VoceId,
) : EventoPubblicato
