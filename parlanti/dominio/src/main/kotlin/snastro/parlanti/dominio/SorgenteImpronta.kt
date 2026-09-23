package snastro.parlanti.dominio

import snastro.kernel.IntervalloMs

/**
 * The bounded audio a `Voce`'s print is extracted from (ADR 0012 Amendment (b) point 1): non-empty,
 * pairwise disjoint intervals in time order. [chiave] is its exact source fingerprint, stored with the
 * print (`impronta_vocale.sorgente_impronta`): equal sources ⇔ equal chiavi.
 */
public data class SorgenteImpronta(val intervalli: List<IntervalloMs>) {
    init {
        require(intervalli.isNotEmpty()) { "una SorgenteImpronta ha almeno un intervallo" }
        require(intervalli.zipWithNext().all { (a, b) -> a.fineMs <= b.inizioMs }) {
            "intervalli non disgiunti o non in ordine temporale: $intervalli"
        }
    }

    /** `"<inizioMs>-<fineMs>"` of each interval in time order, joined by `","` — no hashing. */
    val chiave: String = intervalli.joinToString(",") { "${it.inizioMs}-${it.fineMs}" }

    public companion object {
        /** The ONLY way a print chooses its audio: [selezionaIntervalli] with [BUDGET_IMPRONTA_MS], no cap. */
        public fun di(intervalliVoce: List<IntervalloMs>): SorgenteImpronta {
            require(intervalliVoce.isNotEmpty()) { "una Voce ha sempre almeno un intervallo" }
            return SorgenteImpronta(selezionaIntervalli(intervalliVoce, BUDGET_IMPRONTA_MS, maxIntervalli = null))
        }
    }
}
