package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published Language (boundary `eventi-sintesi`): the Riassunti of the Registrazione were deleted
 * (ADR 0021 §6, ADR 0024).
 */
public data class RiassuntoEliminato(val registrazioneId: RegistrazioneId) : EventoPubblicato
