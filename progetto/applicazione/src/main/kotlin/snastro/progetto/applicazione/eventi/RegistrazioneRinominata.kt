package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId

/** Published Language of the domain event `RegistrazioneRinominata` (boundary `eventi-progetto`, after commit). */
public data class RegistrazioneRinominata(
    val registrazioneId: RegistrazioneId,
    val precedente: String,
    val nuovo: String,
) : EventoPubblicato
