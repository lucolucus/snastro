package snastro.parlanti.applicazione.comandi

import snastro.kernel.RegistrazioneId

/** Command: re-derive the STALE print rows of the Voci of [registrazioneId] (ADR 0012 Amendment (b) point 3). */
public data class RiallineaImpronte(val registrazioneId: RegistrazioneId)
