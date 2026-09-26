package snastro.sintesi.applicazione.porte

import snastro.kernel.ErroreDominio

/**
 * The technical failures of the Sintesi application layer — ONE hierarchy per module (ADR 0003).
 * `EseguiProssimoRiassunto` maps them to the Riassunto's failure reason (ADR 0021 §4).
 */
public sealed interface ErroreApplicazioneSintesi : ErroreDominio {
    /** The LLM model is not installed or cannot be loaded → `modello_non_disponibile`. */
    public data object ModelloNonDisponibile : ErroreApplicazioneSintesi

    /** The runtime's exact count exceeds its context ([token] counted) → `troppo_lunga`. */
    public data class IngressoTroppoLungo(val token: Int) : ErroreApplicazioneSintesi

    /** The runtime failed while generating → `errore_modello`. */
    public data class ErroreRuntime(val motivo: String) : ErroreApplicazioneSintesi

    /** The generated answer does not fit the answer schema → `errore_modello`. */
    public data object RispostaNonValida : ErroreApplicazioneSintesi

    /** The run was cancelled (annullato or interrupted) → nothing is written ([INV-S8]). */
    public data object Annullato : ErroreApplicazioneSintesi
}
