package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.ErroreParlanti.NomeGiaInUso
import snastro.parlanti.dominio.ErroreParlanti.ParlanteNonTrovato
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.ParlanteRinominato
import snastro.parlanti.applicazione.eventi.ParlanteRinominato as ParlanteRinominatoPubblicato

/**
 * `RinominaParlante` (AC-90/AC-91): goes through [snastro.parlanti.dominio.Parlante.rinomina]
 * FIRST for `[INV-13]` (an `eliminato` Parlante refuses every change) — the root's rule always
 * decides before anything else. Only once the root accepts the rename does `[INV-16]` (ADR 0007)
 * get pre-checked, right before `salva`; the repository's unique index is the backstop, so a
 * concurrent-insert violation is never silently dropped. On `Esito.Errore` the transaction rolls
 * back and the in-memory (already-renamed) copy is discarded — nothing is persisted.
 */
public class RinominaParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: RinominaParlante): Esito<Unit> = uow.inTransazione {
        val parlante = parlanti.trova(comando.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(comando.parlanteId))
        Nome.di(comando.nome).poi { nome ->
            parlante.rinomina(nome).poi { evento -> salvaSeNomeLibero(parlante, evento) }
        }
    }

    /** [INV-16] pre-check (ADR 0007), after the root already accepted the rename. */
    private fun salvaSeNomeLibero(parlante: Parlante, evento: ParlanteRinominato): Esito<Unit> {
        if (parlanti.nomeAttivoInUso(parlante.progettoId, parlante.nome, escluso = parlante.id)) {
            return Esito.Errore(NomeGiaInUso(parlante.nome.valore))
        }
        return parlanti.salva(parlante).poi {
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun ParlanteRinominato.pubblicato(): ParlanteRinominatoPubblicato =
    ParlanteRinominatoPubblicato(parlanteId, nome)
