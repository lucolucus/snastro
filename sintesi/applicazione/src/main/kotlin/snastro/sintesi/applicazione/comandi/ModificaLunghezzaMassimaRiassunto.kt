package snastro.sintesi.applicazione.comandi

import snastro.kernel.ProgettoId

/**
 * Command: replaces the per-Progetto lunghezza massima del Riassunto, in parole (INV-S9, [300, 2500]).
 * Never touches an existing Riassunto (INV-S10, ADR 0021 §3).
 */
public data class ModificaLunghezzaMassimaRiassunto(public val progettoId: ProgettoId, public val parole: Int)
