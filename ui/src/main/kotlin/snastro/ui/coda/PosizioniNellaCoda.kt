package snastro.ui.coda

/**
 * `posizioni-nella-coda` (owned here, in-process, consumer-driven contract test —
 * [PosizioniNellaCodaContratto]): the position of every waiting item in the shared FIFO queue of
 * Elaborazioni and Riassunti (ADR 0023 §4). The position is computed by the queue's owner — `:avvio`
 * implements this port (block `avvio-coda-condivisa`, the same pattern as
 * [snastro.ui.modelli.ServizioModelli]); presenters only join it with their own rows, re-reading
 * [istantanea] on every `Cambiamento`.
 */
interface PosizioniNellaCoda {
    fun istantanea(): PosizioniCoda
}
