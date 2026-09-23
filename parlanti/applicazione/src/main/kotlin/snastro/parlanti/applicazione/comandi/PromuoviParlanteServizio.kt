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
import snastro.parlanti.dominio.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlantePromosso as ParlantePromossoPubblicato

/**
 * `PromuoviParlante` (AC-92/AC-93): goes through [snastro.parlanti.dominio.Parlante.promuovi]
 * FIRST for `[INV-13]`/`[INV-18]` (an `eliminato`, or already-`ricorrente`, Parlante refuses the
 * promotion) — the root's rule always decides before anything else. Only once the root accepts the
 * promotion does the optional rename get `[INV-16]` (ADR 0007) pre-checked, right before `salva`,
 * exactly like `RinominaParlante`. On `Esito.Errore` the transaction rolls back and the in-memory
 * (already-promoted) copy is discarded — nothing is persisted.
 */
public class PromuoviParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: PromuoviParlante): Esito<Unit> = uow.inTransazione {
        val parlante = parlanti.trova(comando.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(comando.parlanteId))
        nomeRichiesto(comando.nome).poi { nome ->
            parlante.promuovi(nome).poi { evento -> salvaSeNomeLibero(parlante, nome, evento) }
        }
    }

    /** No rename requested (`null`) passes through unvalidated; a given name is only format-validated here. */
    private fun nomeRichiesto(nome: String?): Esito<Nome?> = if (nome == null) Esito.Ok(null) else Nome.di(nome)

    /** [INV-16] pre-check (ADR 0007), only when a rename was requested, after the root already promoted. */
    private fun salvaSeNomeLibero(parlante: Parlante, nomeRichiesto: Nome?, evento: ParlantePromosso): Esito<Unit> {
        if (nomeRichiesto != null &&
            parlanti.nomeAttivoInUso(parlante.progettoId, nomeRichiesto, escluso = parlante.id)
        ) {
            return Esito.Errore(NomeGiaInUso(nomeRichiesto.valore))
        }
        return parlanti.salva(parlante).poi {
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun ParlantePromosso.pubblicato(): ParlantePromossoPubblicato =
    ParlantePromossoPubblicato(parlanteId, nome, nomeCambiato)
