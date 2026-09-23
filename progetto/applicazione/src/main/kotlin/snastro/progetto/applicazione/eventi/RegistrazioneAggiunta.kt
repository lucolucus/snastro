package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId

/**
 * Published Language of the domain event `RegistrazioneAggiunta` (boundary `eventi-progetto`).
 * After-commit consumers only (view refresh): no synchronous subscriber, and importing never starts an
 * `Elaborazione` (ADR 0014, ADR 0012 Amendment (c)).
 */
public data class RegistrazioneAggiunta(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
) : EventoPubblicato
