package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/**
 * Published Language (boundary `eventi-elaborazione`, ADR 0018): the completion transaction REPLACED the existing
 * `Trascritto` of [registrazioneId] whole. Published only then, BEFORE `ElaborazioneCompletata`, in that same
 * transaction: its synchronous subscriber (the Parlanti purge) runs before the COMMIT; the others after it.
 */
public data class TrascrittoSostituito(val registrazioneId: RegistrazioneId) : EventoPubblicato
