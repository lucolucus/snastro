package snastro.sintesi.applicazione.letture

import snastro.sintesi.applicazione.porte.RiassuntoRepository

/**
 * Public query (boundary `riassunti-in-coda`, ADR 0021 §3, ADR 0023 §1, consumer `avvio-sintesi`): the
 * Riassunto source of the shared FIFO queue. Read-only: no rule lives here, [elenco] only maps
 * [RiassuntoRepository.inAttesa]'s own FIFO order (`richiestoAlle`, ties by id) into the context-agnostic
 * [RiassuntoInCoda] shape — `in_attesa` only (AC-S110): a Riassunto that moved to `in_corso`/`pronto`/`fallito`,
 * or that was removed, never appears.
 */
public class RiassuntiInAttesa(private val riassunti: RiassuntoRepository) {
    /** AC-S110: FIFO by `(richiestoAlle, id)`, `in_attesa` only — the repository's own order, unmodified. */
    public fun elenco(): List<RiassuntoInCoda> =
        riassunti.inAttesa().map { RiassuntoInCoda(it.id.valore, it.incontroId, it.richiestoAlle) }
}
