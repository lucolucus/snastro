package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId

/** Published Language of the domain event `ParlanteEliminato` (boundary `eventi-parlanti`); no `Documento` change. */
public data class ParlanteEliminato(val parlanteId: ParlanteId) : EventoPubblicato
