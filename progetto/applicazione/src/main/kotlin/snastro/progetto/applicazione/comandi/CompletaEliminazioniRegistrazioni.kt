package snastro.progetto.applicazione.comandi

/**
 * Command (actor: sistema, at project open, after the Sbobinatura startup sweep is queued (AC-C47; B51 pre-release
 * triage, 2026-09-29: no longer `RigeneraTuttiIDocumenti`, retired as dead code)): completes the file removals
 * of every Registrazione deleted before a crash or a failed cleanup (ADR 0020 §4).
 */
public data object CompletaEliminazioniRegistrazioni
