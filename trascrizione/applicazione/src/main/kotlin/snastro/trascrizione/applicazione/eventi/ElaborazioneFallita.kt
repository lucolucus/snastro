package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published Language of the domain event `ElaborazioneFallita` (boundary `eventi-elaborazione`); [motivo] is plain
 * Italian.
 */
public data class ElaborazioneFallita(val registrazioneId: RegistrazioneId, val motivo: String) : EventoPubblicato
