package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.Elaborazione

/**
 * Read-model `stati-elaborazione` (AC-162..165, S2): the processing state S2 (`RegistrazioniDelProgetto`)
 * joins onto Progetto's Registrazioni. Read-only: no rule lives here — [stato] is derived only from
 * [Elaborazione]'s own named predicates (`completata`, `fallita`) and `avviataAlle`, never by comparing
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
        val ultima = ultima(id) ?: return nonAvviata(id)
        return when {
            ultima.completata -> completata(id, ultima)
            ultima.fallita -> fallita(id, ultima)
            ultima.avviataAlle == null -> inAttesa(id, inAttesa, ultima)
            else -> inCorso(id, ultima)
        }
    }

    /** AC-162/163..165: the Registrazione's LATEST Elaborazione — deterministic, `creataAlle` then id. */
    private fun ultima(id: RegistrazioneId): Elaborazione? =
        elaborazioni.diRegistrazione(id).maxWithOrNull(compareBy({ it.creataAlle }, { it.id.valore }))

    private fun nonAvviata(id: RegistrazioneId) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.NON_AVVIATA,
        fase = null,
        avviataAlle = null,
        motivoFallimento = null,
        posizioneInCoda = null,
        numVoci = null,
    )

    private fun inAttesa(id: RegistrazioneId, coda: List<Elaborazione>, e: Elaborazione) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.IN_ATTESA,
        fase = null,
        avviataAlle = null,
        motivoFallimento = null,
        posizioneInCoda = coda.indexOfFirst { it.id == e.id }.takeIf { it >= 0 }?.plus(1), // AC-163
        numVoci = null,
    )

    private fun inCorso(id: RegistrazioneId, e: Elaborazione) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.IN_CORSO,
        fase = fasi.faseDi(id), // AC-164
        avviataAlle = e.avviataAlle,
        motivoFallimento = null,
        posizioneInCoda = null,
        numVoci = null,
    )

    private fun completata(id: RegistrazioneId, e: Elaborazione) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.COMPLETATA,
        fase = null,
        avviataAlle = e.avviataAlle,
        motivoFallimento = null,
        posizioneInCoda = null,
        numVoci = trascritti.trova(id)?.voci?.size, // AC-165
    )

    private fun fallita(id: RegistrazioneId, e: Elaborazione) = StatoRegistrazioneVista(
        registrazioneId = id,
        stato = StatoElaborazioneVista.FALLITA,
        fase = null,
        avviataAlle = e.avviataAlle,
        motivoFallimento = e.motivoFallimento,
        posizioneInCoda = null,
        numVoci = null,
    )
}
