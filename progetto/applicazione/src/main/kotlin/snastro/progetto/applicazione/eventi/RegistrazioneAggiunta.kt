package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId

/**
 * Published Language of the domain event `RegistrazioneAggiunta` (boundary `eventi-progetto`).
 * Delivered SYNCHRONOUSLY inside the publishing command's transaction (auto-start of the
 * `Elaborazione`, ADR 0012 R2).
 */
public data class RegistrazioneAggiunta(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
) : EventoPubblicato
