package snastro.parlanti.applicazione.comandi

import snastro.kernel.IncontroId

/** Command: re-derive the STALE print rows of the Voci of [incontroId] (ADR 0012 (b) point 3, ADR 0035 §6). */
public data class RiallineaImpronte(val incontroId: IncontroId)
