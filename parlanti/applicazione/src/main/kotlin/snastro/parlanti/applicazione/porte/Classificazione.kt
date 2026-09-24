package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId

/**
 * The outcome of classifying one frase against a set of reference centroids
 * ([ClassificatoreSomiglianza]) — never a similarity number (ADR 0019 §4.3).
 */
public sealed interface Classificazione {
    /** The frase is confidently attributed to [parlanteId] (best ≥ minima AND best − second ≥ margine). */
    public data class Sicura(val parlanteId: ParlanteId) : Classificazione

    /** Below the minimum, below the margin over the runner-up, a tie, or a non-comparable print. */
    public data object Incerta : Classificazione
}
