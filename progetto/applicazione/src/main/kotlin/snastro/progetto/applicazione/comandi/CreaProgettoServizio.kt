package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.porte.ProgettoRepository
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.Progetto

/**
 * Use-case `CreaProgetto` (AC-53/54/55): on a freshly opened, still empty project database,
 * validates [CreaProgetto.nome] through [NomeProgetto], refuses a second Progetto, saves it and
 * publishes `ProgettoCreato`. Folder layout, DB opening and the project registry are
 * `SessioneProgetto`'s job (block `avvio-composizione`, R3) — out of scope here.
 */
public class CreaProgettoServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val progetti: ProgettoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: CreaProgetto): Esito<Unit> = uow.inTransazione {
        if (progetti.trova() != null) {
            return@inTransazione Esito.Errore(ErroreProgetto.ProgettoGiaPresente)
        }
        NomeProgetto.di(c.nome).poi { nome ->
            val creato = Progetto.crea(ProgettoId(generatoreId.nuovo()), nome)
            progetti.salva(creato.aggregato)
            eventi.pubblica(creato.evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.progetto.dominio.ProgettoCreato.pubblicato(): ProgettoCreato =
    ProgettoCreato(progettoId = id, nome = nome)
