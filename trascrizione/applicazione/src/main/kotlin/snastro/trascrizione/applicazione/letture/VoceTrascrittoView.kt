package snastro.trascrizione.applicazione.letture

import snastro.kernel.VoceId

/**
 * One row of [TrascrittoView.voci]: [etichetta] = `"Voce " + voceId.numero` (AC-167), never a Parlante name;
 * [altreParti] the numbers of the OTHER Parti of the Incontro where the Voce speaks, ascending.
 */
public data class VoceTrascrittoView(val voceId: VoceId, val etichetta: String, val altreParti: List<Int> = emptyList())
