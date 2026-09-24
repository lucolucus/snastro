package snastro.trascrizione.applicazione.comandi

import snastro.kernel.ElaborazioneId

/**
 * Withdraws the queued (`in_attesa`, never started) Elaborazione [elaborazioneId] — a first 'Trascrivi', a
 * 'Riprova' or a 'Ritrascrivi'. Actor: the utente, 'Annulla' on a queued S2 row (ADR 0018 Amendment (b)). A
 * running one cannot be cancelled. See [AnnullaElaborazioneServizio].
 */
public data class AnnullaElaborazione(val elaborazioneId: ElaborazioneId)
