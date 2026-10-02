package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.OraDiInizioModificata
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.ErroreProgetto

/**
 * Use-case `ModificaOraDiInizio` (D-0009): replaces the OraDiInizio of an existing Registrazione and publishes
 * `OraDiInizioModificata` after commit. The same value changes nothing: no write, no event.
 */
public class ModificaOraDiInizioServizio(
    private val uow: UnitaDiLavoro,
    private val registrazioni: RegistrazioneRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ModificaOraDiInizio): Esito<Unit> = uow.inTransazione {
        val registrazione = registrazioni.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(c.registrazioneId))
        registrazione.modificaOraDiInizio(c.ora).poi { evento ->
            if (evento != null) {
                registrazioni.salva(registrazione)
                eventi.pubblica(
                    OraDiInizioModificata(
                        registrazioneId = evento.id,
                        incontroId = evento.incontroId,
                        precedente = evento.precedente?.valore,
                        nuova = evento.nuova?.valore,
                    ),
                )
            }
            Esito.Ok(Unit)
        }
    }
}
