package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.LunghezzaMassimaRiassuntoModificata
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.dominio.LunghezzaMassimaRiassuntoModificataDominio

/**
 * Use-case `ModificaLunghezzaMassimaRiassunto` (AC-S90/S91; INV-S9, INV-S10; ADR 0021 §3):
 * - reads the Progetto's current setting (no row ⇒ [snastro.sintesi.dominio.LunghezzaMassimaRiassunto.predefinita]);
 * - delegates the range to its `modifica`, itself delegating to `LunghezzaMassimaParole.di` (INV-S9): the VO is the
 *   only place [300, 10 000] is checked, this service never re-checks it;
 * - on success, upserts the row and publishes `LunghezzaMassimaRiassuntoModificata`, delivered after commit only
 *   (ADR 0012); on `LunghezzaMassimaFuoriIntervallo`, nothing is written and nothing is published.
 *
 * INV-S10 (never touches a queued, running or `pronto` Riassunto) holds by construction: this service has no
 * `RiassuntoRepository` collaborator, so it cannot read or write a `Riassunto` row.
 *
 * The aggregate publishes its event even when [ModificaLunghezzaMassimaRiassunto.parole] repeats the stored value
 * (a no-op change): the service does not special-case it — the setting's identity is the owner's call, and the
 * feature declares no rule against a redundant notification (an idempotent subscriber sees no observable effect).
 */
public class ModificaLunghezzaMassimaRiassuntoServizio(
    private val uow: UnitaDiLavoro,
    private val lunghezze: LunghezzaMassimaRiassuntoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ModificaLunghezzaMassimaRiassunto): Esito<Unit> = uow.inTransazione {
        val lunghezza = lunghezze.trova(c.progettoId)
        lunghezza.modifica(c.parole).poi { evento ->
            lunghezze.salva(lunghezza).poi {
                eventi.pubblica(evento.pubblicato())
                Esito.Ok(Unit)
            }
        }
    }
}

private fun LunghezzaMassimaRiassuntoModificataDominio.pubblicato(): LunghezzaMassimaRiassuntoModificata =
    LunghezzaMassimaRiassuntoModificata(progettoId)
