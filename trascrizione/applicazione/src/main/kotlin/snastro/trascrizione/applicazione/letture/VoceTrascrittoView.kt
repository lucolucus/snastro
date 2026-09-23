package snastro.trascrizione.applicazione.letture

import snastro.kernel.VoceId

/** One row of [TrascrittoView.voci]: [etichetta] = `"Voce " + voceId.numero` (AC-167), never a Parlante name. */
public data class VoceTrascrittoView(val voceId: VoceId, val etichetta: String)
