package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Published Language of the domain event `ElaborazioneCompletata` (boundary `eventi-trascrizione-incontro`, ADR 0035
 * §5, after commit): the Parte [registrazioneId] of the Incontro [incontroId] has its (new) Trascritto.
 */
public data class ElaborazioneCompletata(val registrazioneId: RegistrazioneId, val incontroId: IncontroId) :
    EventoPubblicato
