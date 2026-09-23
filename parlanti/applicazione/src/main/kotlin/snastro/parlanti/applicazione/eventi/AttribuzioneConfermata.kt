package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef

/**
 * Published Language of the domain event `AttribuzioneConfermata` (boundary `eventi-parlanti`, after commit);
 * [precedente] is the `Parlante` the `Voce` was attributed to before a correction, `null` on a first attribution.
 */
public data class AttribuzioneConfermata(
    val voceRef: VoceRef,
    val parlanteId: ParlanteId,
    val precedente: ParlanteId?,
) : EventoPubblicato
