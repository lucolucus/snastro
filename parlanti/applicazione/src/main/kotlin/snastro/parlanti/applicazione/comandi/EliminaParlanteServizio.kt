package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.parlanti.applicazione.comandi.ErroreApplicazioneParlanti.ParlanteNonTrovato
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.ParlanteEliminato
import snastro.parlanti.applicazione.eventi.ParlanteEliminato as ParlanteEliminatoPubblicato

/**
 * `EliminaParlante` (AC-94/AC-95): goes through [snastro.parlanti.dominio.Parlante.elimina] for
 * `[INV-13]` — the tombstone transaction purges every `ImprontaVocale` in the same call (ADR 0009);
 * `salva` persists the now-empty print list. No `Attribuzione` is touched (out of this block's
 * scope) and no `Documento` `Rigenerazione` is triggered by this event.
 */
public class EliminaParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: EliminaParlante): Esito<Unit> = uow.inTransazione {
        val parlante = parlanti.trova(comando.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(comando.parlanteId))
        parlante.elimina()
            .poi { evento ->
                parlanti.salva(parlante).poi {
                    eventi.pubblica(evento.pubblicato())
                    Esito.Ok(Unit)
                }
            }
    }
}

private fun ParlanteEliminato.pubblicato(): ParlanteEliminatoPubblicato = ParlanteEliminatoPubblicato(parlanteId)
