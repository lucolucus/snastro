package snastro.parlanti.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId

public data class ParlanteCreato(
    val parlanteId: ParlanteId,
    val progettoId: ProgettoId,
    val nome: String,
    val tipo: TipoParlante,
) : EventoDominio

public data class ParlanteRinominato(val parlanteId: ParlanteId, val nome: String) : EventoDominio

public data class ParlantePromosso(
    val parlanteId: ParlanteId,
    val nome: String,
    val nomeCambiato: Boolean,
) : EventoDominio

public data class ParlanteEliminato(val parlanteId: ParlanteId) : EventoDominio
