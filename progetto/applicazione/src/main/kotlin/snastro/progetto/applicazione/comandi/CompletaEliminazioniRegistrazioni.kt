package snastro.progetto.applicazione.comandi

/**
 * Command (actor: sistema, at project open, after `RigeneraTuttiIDocumenti` is queued): completes the file removals
 * of every Registrazione deleted before a crash or a failed cleanup (ADR 0020 §4).
 */
public data object CompletaEliminazioniRegistrazioni
