package snastro.parlanti.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Parlanti's own copy of a Registrazione as read through [LettoreRegistrazione] (pinned view):
 * [progettoId] scopes INV-17, [dataRegistrazione] feeds 'Ospite del dd/MM/yyyy' (INV-19).
 */
public data class RegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
    val titolo: String,
    val riferimentoAudio: RiferimentoAudio,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
