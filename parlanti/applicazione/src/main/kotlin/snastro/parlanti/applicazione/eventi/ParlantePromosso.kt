package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId

/**
 * Published Language of the domain event `ParlantePromosso` (boundary `eventi-parlanti`, after commit);
 * [nomeCambiato] lets the Documento policy regenerate only when the `Nome` changed (R22).
 */
public data class ParlantePromosso(
    val parlanteId: ParlanteId,
    val nome: String,
    val nomeCambiato: Boolean,
) : EventoPubblicato
