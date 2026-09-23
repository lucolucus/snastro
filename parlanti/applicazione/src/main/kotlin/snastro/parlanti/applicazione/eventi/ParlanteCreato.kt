package snastro.parlanti.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId

/** Published Language of the domain event `ParlanteCreato` (boundary `eventi-parlanti`, after commit). */
public data class ParlanteCreato(
    val parlanteId: ParlanteId,
    val progettoId: ProgettoId,
    val nome: String,
    val tipo: TipoParlanteVista,
) : EventoPubblicato
