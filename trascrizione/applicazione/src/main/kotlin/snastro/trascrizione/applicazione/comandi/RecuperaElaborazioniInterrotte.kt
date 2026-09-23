package snastro.trascrizione.applicazione.comandi

/**
 * Command (actor: sistema, startup): every `in_corso` Elaborazione left over from a crash (no live run
 * survives a restart) becomes `fallita('interrotta')`, retryable (AC-74). Leaves every `in_attesa`,
 * `completata` and `fallita` Elaborazione untouched (AC-75).
 */
public data object RecuperaElaborazioniInterrotte
