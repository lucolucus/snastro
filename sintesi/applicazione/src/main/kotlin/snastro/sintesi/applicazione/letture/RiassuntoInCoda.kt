package snastro.sintesi.applicazione.letture

import snastro.kernel.IncontroId
import java.time.Instant

/**
 * One `in_attesa` Riassunto as the shared queue (`:avvio`, ADR 0023 §1) sees it: [riassuntoId] is a plain
 * `String` (not [snastro.sintesi.dominio.RiassuntoId]) ON PURPOSE — the queue is context-agnostic and stays
 * over primitive ids only, so it never depends on Sintesi's Published Language types. [richiestoAlle] is the
 * FIFO key (AC-S110), at the millisecond precision the SQL store keeps, so the queue's `teste`/`prossima`
 * never disagree with [RiassuntiInAttesa.elenco]'s own order.
 */
public data class RiassuntoInCoda(
    public val riassuntoId: String,
    public val incontroId: IncontroId,
    public val richiestoAlle: Instant,
)
