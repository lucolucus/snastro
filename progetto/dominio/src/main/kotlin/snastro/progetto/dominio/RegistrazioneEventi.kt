package snastro.progetto.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/** A new [Registrazione] was added to a [Progetto]. */
public data class RegistrazioneAggiunta(val id: RegistrazioneId, val progettoId: ProgettoId) : EventoDominio

/** The user replaced the DataRegistrazione of a [Registrazione]. */
public data class DataRegistrazioneModificata(
    val id: RegistrazioneId,
    val precedente: LocalDate,
    val nuova: LocalDate,
) : EventoDominio

/** The user renamed a [Registrazione] (AC-360): only its titolo changed, never its audio file (AC-362). */
public data class RegistrazioneRinominata(
    val id: RegistrazioneId,
    val precedente: String,
    val nuovo: String,
) : EventoDominio

/**
 * The user deleted a [Registrazione] (ADR 0020): the values AT deletion (current titolo and date, the audio
 * reference) — after the COMMIT the Registrazione no longer exists and these are the only way to find its files.
 */
public data class RegistrazioneEliminata(
    val id: RegistrazioneId,
    val progettoId: ProgettoId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val riferimentoAudio: RiferimentoAudio,
) : EventoDominio
