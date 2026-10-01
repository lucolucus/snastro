package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Parlanti's own copy of a Registrazione as read through [LettoreRegistrazione] (pinned view):
 * [progettoId] scopes INV-17, [dataRegistrazione] feeds 'Ospite del dd/MM/yyyy' (INV-19), [incontroId] is the Incontro
 * the Registrazione is a Parte of (ADR 0033 §4.1, final shape).
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
