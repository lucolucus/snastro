package snastro.sintesi.applicazione.comandi

import snastro.sintesi.dominio.RiassuntoId

/**
 * Outcome of one [EseguiProssimoRiassunto] attempt: which Riassunto, if any, was picked as the FIFO
 * head — after excluding [EseguiProssimoRiassunto.esclusi] and honouring [EseguiProssimoRiassunto.primaDi]
 * — and run to its own conclusion (`pronto`, `fallito`, or left untouched by an INV-S8 race).
 *
 * `eventi-sintesi` has NO synchronous subscriber (ADR 0021 §3), so the claim's own transaction is never
 * refused by a policy: unlike `EseguiProssimaElaborazione`'s `RisultatoAvanzamento`, there is no
 * `AvvioRifiutato` case here.
 */
public sealed interface RisultatoRiassunto {
    /**
     * No `in_attesa` Riassunto was eligible: the queue is empty, every head is in
     * [EseguiProssimoRiassunto.esclusi], or [EseguiProssimoRiassunto.primaDi] refused the oldest one.
     */
    public data object Nessuno : RisultatoRiassunto

    /** [id] was picked, started, and run through the model (whatever ITS OWN outcome turns out to be). */
    public data class Avviato(public val id: RiassuntoId) : RisultatoRiassunto
}
