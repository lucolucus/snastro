package snastro.avvio.r2

import snastro.kernel.ErroreDominio

/**
 * The technical failures of `:avvio`'s own adapters (the composition root owns the project-folder layout,
 * architecture.md R3) — ONE sealed hierarchy, like each context's `ErroreApplicazione<Contesto>` (CR-8). Only
 * services that merely branch on Ok/Errore ever see it, never a screen.
 */
internal sealed interface ErroreApplicazioneAvvio : ErroreDominio {
    /**
     * A derived file ([percorso], relative to the project folder) of a deleted Registrazione could not be removed:
     * `CompletaEliminazioniRegistrazioni` keeps its pending row for the next open (ADR 0020 §4).
     */
    data class DerivatoNonRimosso(val percorso: String, val motivo: String) : ErroreApplicazioneAvvio
}
