package snastro.trascrizione.applicazione.comandi

import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDominio

/**
 * Outcome of one [EseguiProssimaElaborazione] attempt (AC-313): which Elaborazione, if any, was
 * picked as the FIFO head (after excluding `esclusi`), and whether starting it was accepted.
 *
 * The dispatcher (`:avvio`) needs the attempted id even when the avvio is REFUSED, to grow its own
 * per-session exclusion set (rather than spinning on the same stuck head) — so a refusal is reported
 * here as a normal, expected outcome ([AvvioRifiutato], wrapped in `Esito.Ok`), never as
 * `Esito.Errore`: nothing about attempting a head and having it refused is an unexpected servizio
 * failure (`EseguiProssimaElaborazioneServizio.esegui` has no other `Esito.Errore` source left once
 * this is out of the way).
 */
public sealed interface RisultatoAvanzamento {
    /** No `in_attesa` Elaborazione was eligible: the queue is empty, or every head is in `esclusi`. */
    public data object NessunElemento : RisultatoAvanzamento

    /** [id] was picked, started, and run through the pipeline (whatever ITS OWN outcome turns out to be). */
    public data class Avviata(public val id: ElaborazioneId) : RisultatoAvanzamento

    /** [id]'s `in_attesa → in_corso` transaction was refused ([causa]); it is still `in_attesa`. */
    public data class AvvioRifiutato(
        public val id: ElaborazioneId,
        public val causa: ErroreDominio,
    ) : RisultatoAvanzamento
}
