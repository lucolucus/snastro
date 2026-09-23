package snastro.parlanti.applicazione.comandi

import snastro.kernel.ParlanteId

/** Command: tombstones [parlanteId] — every print purged, the Nome kept (AC-94/AC-95). */
public data class EliminaParlante(val parlanteId: ParlanteId)
