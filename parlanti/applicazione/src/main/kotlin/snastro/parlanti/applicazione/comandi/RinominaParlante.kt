package snastro.parlanti.applicazione.comandi

import snastro.kernel.ParlanteId

/** Command: renames [parlanteId] to the raw, not-yet-validated [nome] (AC-90/AC-91). */
public data class RinominaParlante(val parlanteId: ParlanteId, val nome: String)
