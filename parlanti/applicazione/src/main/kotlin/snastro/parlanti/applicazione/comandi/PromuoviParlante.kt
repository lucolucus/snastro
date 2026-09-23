package snastro.parlanti.applicazione.comandi

import snastro.kernel.ParlanteId

/** Command: promotes [parlanteId] to `ricorrente`; [nome] is a facoltativa rename (AC-92/AC-93). */
public data class PromuoviParlante(val parlanteId: ParlanteId, val nome: String?)
