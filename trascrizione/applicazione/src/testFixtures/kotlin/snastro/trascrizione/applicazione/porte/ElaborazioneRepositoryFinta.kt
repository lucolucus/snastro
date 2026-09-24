package snastro.trascrizione.applicazione.porte

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant

/**
 * In-memory [ElaborazioneRepository], a stand-in for the `elaborazione` table: refuses INV-4 like the partial
 * unique index `elaborazione_aperta_unica` of ADR 0007 (several completata allowed, ADR 0018), deletes like the
 * compare-and-delete `eliminaInAttesa` and stores immutable rows, rebuilding a fresh [Elaborazione] through its own
 * transitions on every read (reconstitution is reserved to persistence adapters, CR-15) — so no caller ever
 * aliases the stored state. [Ripristinabile]: pass it to `UnitaDiLavoroFinta`.
 */
public class ElaborazioneRepositoryFinta : ElaborazioneRepository, Ripristinabile {
    private val righe = LinkedHashMap<ElaborazioneId, Riga>()

    override fun diRegistrazione(id: RegistrazioneId): List<Elaborazione> =
        righe.values.filter { it.registrazioneId == id }.map { it.inDominio() }

    override fun inAttesa(): List<Elaborazione> = conStato(StatoElaborazione.IN_ATTESA)

    override fun inCorso(): List<Elaborazione> = conStato(StatoElaborazione.IN_CORSO)

    override fun trova(id: ElaborazioneId): Elaborazione? = righe[id]?.inDominio()

    override fun rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> {
        val riga = righe[id] ?: return Esito.Errore(ElaborazioneNonTrovata(id))
        if (riga.stato != StatoElaborazione.IN_ATTESA) return Esito.Errore(ElaborazioneGiaAvviata(id))
        righe.remove(id)
        return Esito.Ok(Unit)
    }

    override fun salva(e: Elaborazione): Esito<Unit> {
        val altre = righe.values.filter { it.registrazioneId == e.registrazioneId && it.id != e.id }
        return when {
            e.aperta && altre.any { it.aperta } -> Esito.Errore(ElaborazioneGiaAperta(e.registrazioneId))
            else -> {
                righe[e.id] = Riga(e)
                Esito.Ok(Unit)
            }
        }
    }

    override fun istantanea(): () -> Unit {
        val salvate = LinkedHashMap(righe)
        return {
            righe.clear()
            righe.putAll(salvate)
        }
    }

    private fun conStato(stato: StatoElaborazione): List<Elaborazione> =
        righe.values
            .filter { it.stato == stato }
            .sortedWith(compareBy({ it.creataAlle }, { it.id.valore }))
            .map { it.inDominio() }

    /** The persisted fields of one `elaborazione` row. */
    private class Riga(e: Elaborazione) {
        val id = e.id
        val registrazioneId = e.registrazioneId
        val creataAlle: Instant = e.creataAlle
        val numeroPersone = e.numeroPersone
        val stato = e.stato
        val avviataAlle = e.avviataAlle
        val motivoFallimento = e.motivoFallimento
        val aperta = e.aperta

        fun inDominio(): Elaborazione =
            unaElaborazione(
                stato = stato,
                id = id,
                registrazioneId = registrazioneId,
                creataAlle = creataAlle,
                avviataAlle = avviataAlle ?: creataAlle,
                motivo = motivoFallimento.orEmpty(),
                numeroPersone = numeroPersone,
            )
    }
}
