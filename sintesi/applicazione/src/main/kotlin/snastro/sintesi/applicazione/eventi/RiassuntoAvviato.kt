package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId

/** Published Language (boundary `eventi-sintesi`): a Riassunto of the Registrazione moved to `in_corso`. */
public data class RiassuntoAvviato(val incontroId: IncontroId) : EventoPubblicato
