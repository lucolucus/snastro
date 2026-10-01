package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto

/**
 * Use-case `RecuperaRiassuntiInterrotti` (AC-S89, ADR 0023 §2 crash/escape recovery): at startup, or
 * right after the queue's escape recovery, no model run is ever live — so every `in_corso` Riassunto
 * found is one a previous run left behind and becomes `fallito('interrotto')`, a terminal state
 * (INV-S1) the user can retry from (`Riassumi`). Every `in_attesa`, `pronto` and `fallito` Riassunto is
 * never read by this command's repository call, and running it twice changes nothing more.
 */
public class RecuperaRiassuntiInterrottiServizio(
    private val uow: UnitaDiLavoro,
    private val riassunti: RiassuntoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(ignored: RecuperaRiassuntiInterrotti): Esito<Unit> = uow.inTransazione {
        riassunti.inCorso().fold<Riassunto, Esito<Unit>>(Esito.Ok(Unit)) { esito, r -> esito.poi { interrompi(r) } }
    }

    private fun interrompi(r: Riassunto): Esito<Unit> =
        r.fallisci(MotivoFallimento.INTERROTTO).poi { evento ->
            riassunti.salva(r).poi {
                eventi.pubblica(RiassuntoFallito(evento.incontroId, evento.motivo.codice))
                Esito.Ok(Unit)
            }
        }
}
