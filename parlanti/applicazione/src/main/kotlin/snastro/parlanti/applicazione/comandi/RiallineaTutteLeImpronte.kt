package snastro.parlanti.applicazione.comandi

import snastro.kernel.ProgettoId

/** Command: [RiallineaImpronte] for every Registrazione of [progettoId] holding print rows (at project open). */
public data class RiallineaTutteLeImpronte(val progettoId: ProgettoId)
