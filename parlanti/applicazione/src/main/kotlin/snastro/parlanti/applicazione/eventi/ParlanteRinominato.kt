package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId

/** Published Language of the domain event `ParlanteRinominato` (boundary `eventi-parlanti`, after commit). */
public data class ParlanteRinominato(val parlanteId: ParlanteId, val nome: String) : EventoPubblicato
