package snastro.sintesi.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId

/** Published Language (boundary `eventi-sintesi`): the lunghezza massima del Riassunto of the Progetto changed. */
public data class LunghezzaMassimaRiassuntoModificata(val progettoId: ProgettoId) : EventoPubblicato
