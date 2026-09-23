package snastro.trascrizione.applicazione.comandi

/**
 * Command (actor: sistema, R17): runs the oldest `in_attesa` Elaborazione through the local pipeline
 * (AC-68). No effect if none exists.
 */
public data object EseguiProssimaElaborazione
