package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId

/** Published Language of the domain event `ProgettoCreato` (boundary `eventi-progetto`, ADR 0012). */
public data class ProgettoCreato(val progettoId: ProgettoId, val nome: String) : EventoPubblicato
