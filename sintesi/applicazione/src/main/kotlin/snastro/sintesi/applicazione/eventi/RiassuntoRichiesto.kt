package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId

/** Published Language (boundary `eventi-sintesi`): a Riassunto of the Registrazione was queued `in_attesa`. */
public data class RiassuntoRichiesto(val incontroId: IncontroId) : EventoPubblicato
