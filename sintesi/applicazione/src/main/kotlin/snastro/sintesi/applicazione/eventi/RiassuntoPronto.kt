package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId

/** Published Language (boundary `eventi-sintesi`): a Riassunto of the Registrazione was committed `pronto`. */
public data class RiassuntoPronto(val incontroId: IncontroId) : EventoPubblicato
