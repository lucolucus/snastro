package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.Elaborazione

/**
 * Read-model `stati-elaborazione` (AC-162..165, S2): the processing state S2 (`RegistrazioniDelProgetto`)
 * joins onto Progetto's Registrazioni. Read-only: no rule lives here — [stato] is derived only from
 * [Elaborazione]'s own named predicates (`completata`, `fallita`, `inAttesa`), never by comparing
 * `StatoElaborazione` (§14 gate); [FasiInCorso] is read, never decided on.
 *
 * ADR 0023 §4 (block `avvio-coda-condivisa`, `enforced_by`): the queue position no longer lives here —
 * S2's presenter reads it from `:ui`'s `PosizioniNellaCoda` (computed by the shared queue's owner,
 * `:avvio`) and joins it by [RegistrazioneId] itself, so there is one source of truth across both queued
 * kinds (Elaborazione, Riassunto).
 */
public class StatiElaborazione(
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: VociDellIncontroRepository,
    private val fasi: FasiInCorso,
) {
    /** AC-162: one row per id of [registrazioneIds], same order, each built from its LATEST Elaborazione. */
    public fun stati(registrazioneIds: List<RegistrazioneId>): List<StatoRegistrazioneVista> =
        registrazioneIds.map(::riga)

    /**
     * AC-I41 (ADR 0033 §4): an open run wins (a re-run of a transcribed Parte too), then the Trascritto, then a failed
     * run, else nothing yet. The latest-run rule is [stati]' own ([statoDi]); the Trascritto is only tested for
     * existence ([VociDellIncontroRepository.conTrascritto], ids only), never loaded, and only when no run is open.
     */
    public fun statoParte(r: RegistrazioneId): StatoParte {
        val stato = statoDi(ultima(r))
        return when {
            stato == StatoElaborazioneVista.IN_ATTESA || stato == StatoElaborazioneVista.IN_CORSO ->
                StatoParte.IN_TRASCRIZIONE
            r in trascritti.conTrascritto() -> StatoParte.TRASCRITTA
            stato == StatoElaborazioneVista.FALLITA -> StatoParte.NON_RIUSCITA
            else -> StatoParte.DA_TRASCRIVERE
        }
    }

    private fun riga(id: RegistrazioneId): StatoRegistrazioneVista {
        val ultima = ultima(id)
        val trascritto = trascritti.trascritto(id) // ADR 0018: whatever the latest run's state (AC-165/AC-447)
        val stato = statoDi(ultima)
        return StatoRegistrazioneVista(
            registrazioneId = id,
            stato = stato,
            fase = fasi.faseDi(id).takeIf { stato == StatoElaborazioneVista.IN_CORSO }, // AC-164
            avviataAlle = ultima?.avviataAlle,
            motivoFallimento = ultima?.motivoFallimento.takeIf { stato == StatoElaborazioneVista.FALLITA },
            numVoci = trascritto?.voci?.size,
            numeroPersone = ultima?.numeroPersone?.valore,
            trascrittoDisponibile = trascritto != null,
            elaborazioneId = ultima?.id, // AC-474
        )
    }

    private fun statoDi(ultima: Elaborazione?): StatoElaborazioneVista = when {
        ultima == null -> StatoElaborazioneVista.NON_AVVIATA
        ultima.completata -> StatoElaborazioneVista.COMPLETATA
        ultima.fallita -> StatoElaborazioneVista.FALLITA
        ultima.inAttesa -> StatoElaborazioneVista.IN_ATTESA
        else -> StatoElaborazioneVista.IN_CORSO
    }

    /** AC-162/AC-447: the Registrazione's LATEST Elaborazione — deterministic, `creataAlle` then id (ADR 0018). */
    private fun ultima(id: RegistrazioneId): Elaborazione? =
        elaborazioni.diRegistrazione(id).maxWithOrNull(compareBy({ it.creataAlle }, { it.id.valore }))
}
