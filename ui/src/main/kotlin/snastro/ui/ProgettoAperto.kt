package snastro.ui

import snastro.kernel.ProgettoId

/** `tec-shell-ui`: the Progetto [SessioneProgetto] currently has open (Published Language, R3/R8). */
data class ProgettoAperto(val progettoId: ProgettoId, val nome: String, val percorso: String)
