package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/** Published Language of the domain event `ElaborazioneCompletata` (boundary `eventi-elaborazione`, after commit). */
public data class ElaborazioneCompletata(val registrazioneId: RegistrazioneId) : EventoPubblicato
