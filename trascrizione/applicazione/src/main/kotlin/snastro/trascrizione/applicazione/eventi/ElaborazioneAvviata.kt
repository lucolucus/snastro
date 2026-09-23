package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import java.time.Instant

/** Published Language of the domain event `ElaborazioneAvviata` (boundary `eventi-elaborazione`, after commit). */
public data class ElaborazioneAvviata(val registrazioneId: RegistrazioneId, val avviataAlle: Instant) : EventoPubblicato
