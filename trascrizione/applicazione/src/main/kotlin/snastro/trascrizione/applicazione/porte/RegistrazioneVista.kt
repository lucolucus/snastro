package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Trascrizione's own copy of a Registrazione as read through [LettoreRegistrazione] (pinned view); [incontroId] is the
 * Incontro it is a Parte of, the key of the Voce counter (ADR 0033 §4.1, final shape).
 */
public data class RegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
    val incontroId: IncontroId,
    val titolo: String,
    val riferimentoAudio: RiferimentoAudio,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
