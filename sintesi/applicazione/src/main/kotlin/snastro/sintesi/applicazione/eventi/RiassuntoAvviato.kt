package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/** Published Language (boundary `eventi-sintesi`): a Riassunto of the Registrazione moved to `in_corso`. */
public data class RiassuntoAvviato(val registrazioneId: RegistrazioneId) : EventoPubblicato
