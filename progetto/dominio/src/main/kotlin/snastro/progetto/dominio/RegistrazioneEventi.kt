package snastro.progetto.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/** A new [Registrazione] was added to a [Progetto]. */
public data class RegistrazioneAggiunta(val id: RegistrazioneId, val progettoId: ProgettoId) : EventoDominio

/** The user replaced the DataRegistrazione of a [Registrazione]. */
public data class DataRegistrazioneModificata(
    val id: RegistrazioneId,
    val precedente: LocalDate,
    val nuova: LocalDate,
) : EventoDominio
