package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.ErroreProgetto

/**
 * Use-case `ModificaDataRegistrazione` (AC-62/63): replaces the DataRegistrazione of an existing
 * Registrazione and publishes `DataRegistrazioneModificata` — an AFTER-COMMIT consumer regenerates
 * the Documento (ADR 0012).
 */
public class ModificaDataRegistrazioneServizio(
    private val uow: UnitaDiLavoro,
    private val registrazioni: RegistrazioneRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ModificaDataRegistrazione): Esito<Unit> = uow.inTransazione {
        val registrazione = registrazioni.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(c.registrazioneId))
        registrazione.modificaData(c.nuovaData).poi { evento ->
            registrazioni.salva(registrazione)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.progetto.dominio.DataRegistrazioneModificata.pubblicato(): DataRegistrazioneModificata =
    DataRegistrazioneModificata(registrazioneId = id, precedente = precedente, nuova = nuova)
