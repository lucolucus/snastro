package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * Published Language of the domain event `DataRegistrazioneModificata` (boundary `eventi-progetto`, + [incontroId]
 * ADR 0033 §3, after commit).
 */
public data class DataRegistrazioneModificata(
    val registrazioneId: RegistrazioneId,
    val precedente: LocalDate,
    val nuova: LocalDate,
    val incontroId: IncontroId,
) : EventoPubblicato
