package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/** Published Language of the domain event `DataRegistrazioneModificata` (boundary `eventi-progetto`, after commit). */
public data class DataRegistrazioneModificata(
    val registrazioneId: RegistrazioneId,
    val precedente: LocalDate,
    val nuova: LocalDate,
) : EventoPubblicato
