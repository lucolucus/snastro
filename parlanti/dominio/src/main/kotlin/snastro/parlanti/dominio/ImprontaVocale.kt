package snastro.parlanti.dominio

import snastro.kernel.VoceRef

/**
 * One print, kept individually (never averaged) — owned by a [Parlante], keyed by [voceRef] ([INV-14]).
 * [sorgente] is the `SorgenteImpronta.chiave` it was extracted from, [modello] the extractor's model id
 * (ADR 0012 Amendment (b)): together they decide staleness ([obsoleta]).
 */
public data class ImprontaVocale(
    val voceRef: VoceRef,
    val impronta: Impronta,
    val sorgente: String,
    val modello: String,
) {
    /** The ONE staleness rule: stale iff extracted from another source or by another model. */
    public fun obsoleta(chiaveCorrente: String, modelloCorrente: String): Boolean =
        obsoleta(sorgente, modello, chiaveCorrente, modelloCorrente)

    public companion object {
        /** Same rule as the member [obsoleta], for callers holding only the row metadata. */
        public fun obsoleta(
            sorgente: String,
            modello: String,
            chiaveCorrente: String,
            modelloCorrente: String,
        ): Boolean = sorgente != chiaveCorrente || modello != modelloCorrente
    }
}
