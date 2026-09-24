package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.ElaborazioneAnnullata
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata

/**
 * AC-464..AC-468 (ADR 0018 Amendment (b) §3): in ONE transaction, `trova` → [Elaborazione.annulla] (the root's
 * check: only `in_attesa`) → [ElaborazioneRepository.rimuoviInAttesa] → publish [ElaborazioneAnnullata]
 * (after-commit subscribers only). The compare-and-delete is what settles the race with the dispatcher's claim
 * (both `BEGIN IMMEDIATE`): if the claim committed first, the delete answers `ElaborazioneGiaAvviata` even when
 * this service's own read was stale, and it is returned unchanged — nothing written, nothing published. Never
 * reads or writes the Trascritto; the Registrazione falls back to its remaining latest Elaborazione.
 */
public class AnnullaElaborazioneServizio(
    private val uow: UnitaDiLavoro,
    private val elaborazioni: ElaborazioneRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: AnnullaElaborazione): Esito<Unit> = uow.inTransazione {
        val id = comando.elaborazioneId
        val elaborazione = elaborazioni.trova(id) ?: return@inTransazione Esito.Errore(ElaborazioneNonTrovata(id))
        elaborazione.annulla().poi { annullata ->
            elaborazioni.rimuoviInAttesa(id).poi {
                eventi.pubblica(ElaborazioneAnnullata(annullata.registrazioneId))
                Esito.Ok(Unit)
            }
        }
    }
}
