package snastro.ui.coda

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * A snapshot of the shared queue (ADR 0023 §4): each position is 1-based over ALL `in_attesa` items
 * of both kinds in the queue's global order `(istante, kind, id)`; an item `in_corso` is not counted.
 * Keying is exact per kind: [elaborazioni] by [RegistrazioneId] (INV-4), [riassunti] by [IncontroId] (one
 * Riassunto per Incontro, INV-S2, AC-I51). A key absent from a map has no waiting item of that kind — its lookup
 * yields `null` and the screen shows "In coda" without a number (AC-S24).
 */
data class PosizioniCoda(
    val elaborazioni: Map<RegistrazioneId, Int>,
    val riassunti: Map<IncontroId, Int>,
) {
    companion object {
        /** An empty queue: both maps empty (AC-S24). */
        val VUOTA: PosizioniCoda = PosizioniCoda(emptyMap(), emptyMap())
    }
}
