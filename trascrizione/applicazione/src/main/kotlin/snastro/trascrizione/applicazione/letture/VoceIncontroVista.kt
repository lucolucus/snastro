package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * Published-language view of one Voce of an Incontro (boundary `api-voci-incontro`): its [intervalliPerParte] only,
 * never text. A Voce spanning Parti comes ONCE; the map holds only the Parti where it speaks, in Parte order, each
 * list ordered by inizio then segmentoId (INV-7).
 */
public data class VoceIncontroVista(
    val voceRef: VoceRef,
    val intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>,
)
