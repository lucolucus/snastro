package snastro.sintesi.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.ProgettoId

public data class LunghezzaMassimaRiassuntoModificataDominio(val progettoId: ProgettoId) : EventoDominio
