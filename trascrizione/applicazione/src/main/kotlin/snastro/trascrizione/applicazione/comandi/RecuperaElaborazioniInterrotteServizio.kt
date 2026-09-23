package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.dominio.Elaborazione

/**
 * Use-case `RecuperaElaborazioniInterrotte` (AC-74/75, ADR 0004 crash recovery): at startup no pipeline
 * run is ever live, so every `in_corso` Elaborazione found is one a previous run left behind (crash or
 * forced quit) and becomes `fallita('interrotta')` — a state INV-4 lets the user retry from. Every other
 * Elaborazione (`in_attesa`, `completata`, `fallita`) is never read by this command's repository call.
 */
public class RecuperaElaborazioniInterrotteServizio(
    private val uow: UnitaDiLavoro,
    private val elaborazioni: ElaborazioneRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(ignored: RecuperaElaborazioniInterrotte): Esito<Unit> = uow.inTransazione {
        elaborazioni.inCorso().fold<Elaborazione, Esito<Unit>>(Esito.Ok(Unit)) { esito, e ->
            esito.poi { interrompi(e) }
        }
    }

    private fun interrompi(e: Elaborazione): Esito<Unit> =
        e.fallisci(MOTIVO_INTERROTTA).poi { evento ->
            elaborazioni.salva(e).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }

    private companion object {
        const val MOTIVO_INTERROTTA = "interrotta"
    }
}

private fun snastro.trascrizione.dominio.ElaborazioneFallita.pubblicato(): ElaborazioneFallita =
    ElaborazioneFallita(registrazioneId = registrazioneId, motivo = motivo)
