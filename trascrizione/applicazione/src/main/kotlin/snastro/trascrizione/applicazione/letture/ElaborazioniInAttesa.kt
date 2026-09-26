package snastro.trascrizione.applicazione.letture

import snastro.trascrizione.applicazione.porte.ElaborazioneRepository

/**
 * Public query (boundary `elaborazioni-in-coda`, ADR 0023 §1, consumer `avvio-coda-condivisa`): the
 * Elaborazione source of the shared FIFO queue. Read-only: no rule lives here, [elenco] only maps
 * [ElaborazioneRepository.inAttesa]'s own FIFO order (`creataAlle`, ties by id) into the
 * context-agnostic [ElaborazioneInCoda] shape — `in_attesa` only (AC-S23): an Elaborazione that moved
 * to `in_corso`/`completata`/`fallita`, or that was deleted (`AnnullaElaborazione`), never appears.
 */
public class ElaborazioniInAttesa(private val elaborazioni: ElaborazioneRepository) {
    /** AC-S23: FIFO by `(creataAlle, id)`, `in_attesa` only — the repository's own order, unmodified. */
    public fun elenco(): List<ElaborazioneInCoda> =
        elaborazioni.inAttesa().map { ElaborazioneInCoda(it.id.valore, it.registrazioneId, it.creataAlle) }
}
