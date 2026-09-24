package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.Elaborazione

/**
 * Read-model `stati-elaborazione` (AC-162..165, S2): the processing state S2 (`RegistrazioniDelProgetto`)
 * joins onto Progetto's Registrazioni. Read-only: no rule lives here — [stato] is derived only from
 * [Elaborazione]'s own named predicates (`completata`, `fallita`, `inAttesa`), never by comparing
 * `StatoElaborazione` (§14 gate); [posizioneInCoda] is a rank over [ElaborazioneRepository.inAttesa]'s own
 * FIFO order; [FasiInCorso] is read, never decided on.
 */
public class StatiElaborazione(
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: TrascrittoRepository,
    private val fasi: FasiInCorso,
) {
    /** AC-162: one row per id of [registrazioneIds], same order, each built from its LATEST Elaborazione. */
    public fun stati(registrazioneIds: List<RegistrazioneId>): List<StatoRegistrazioneVista> {
        val inAttesa = elaborazioni.inAttesa() // one FIFO snapshot shared by every row (AC-163)
        return registrazioneIds.map { riga(it, inAttesa) }
    }

    private fun riga(id: RegistrazioneId, inAttesa: List<Elaborazione>): StatoRegistrazioneVista {
        val ultima = ultima(id)
        val trascritto = trascritti.trova(id) // ADR 0018: whatever the latest run's state (AC-165/AC-447)
        val stato = when {
            ultima == null -> StatoElaborazioneVista.NON_AVVIATA
            ultima.completata -> StatoElaborazioneVista.COMPLETATA
            ultima.fallita -> StatoElaborazioneVista.FALLITA
            ultima.inAttesa -> StatoElaborazioneVista.IN_ATTESA
            else -> StatoElaborazioneVista.IN_CORSO
        }
        return StatoRegistrazioneVista(
            registrazioneId = id,
            stato = stato,
            fase = fasi.faseDi(id).takeIf { stato == StatoElaborazioneVista.IN_CORSO }, // AC-164
            avviataAlle = ultima?.avviataAlle,
            motivoFallimento = ultima?.motivoFallimento.takeIf { stato == StatoElaborazioneVista.FALLITA },
            posizioneInCoda = inAttesa.indexOfFirst { it.id == ultima?.id }.takeIf { it >= 0 }?.plus(1), // AC-163
            numVoci = trascritto?.voci?.size,
            numeroPersone = ultima?.numeroPersone?.valore,
            trascrittoDisponibile = trascritto != null,
            elaborazioneId = ultima?.id, // AC-474
        )
    }

    /** AC-162/AC-447: the Registrazione's LATEST Elaborazione — deterministic, `creataAlle` then id (ADR 0018). */
    private fun ultima(id: RegistrazioneId): Elaborazione? =
        elaborazioni.diRegistrazione(id).maxWithOrNull(compareBy({ it.creataAlle }, { it.id.valore }))
}
