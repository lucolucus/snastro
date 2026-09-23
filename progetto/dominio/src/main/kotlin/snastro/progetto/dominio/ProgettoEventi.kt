package snastro.progetto.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ProgettoId

/** A new [Progetto] was created. */
public data class ProgettoCreato(val id: ProgettoId, val nome: String) : EventoDominio
