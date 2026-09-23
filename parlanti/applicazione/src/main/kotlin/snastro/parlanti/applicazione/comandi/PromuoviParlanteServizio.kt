package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.parlanti.applicazione.comandi.ErroreApplicazioneParlanti.ParlanteNonTrovato
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.ErroreParlanti.NomeGiaInUso
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlantePromosso as ParlantePromossoPubblicato

/**
 * `PromuoviParlante` (AC-92/AC-93): goes through [snastro.parlanti.dominio.Parlante.promuovi] for
 * `[INV-18]` (only `occasionale` -> `ricorrente`; prints untouched); the optional rename pre-checks
 * `[INV-16]` (ADR 0007) exactly like `RinominaParlante` before touching the root.
 */
public class PromuoviParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: PromuoviParlante): Esito<Unit> = uow.inTransazione {
        val parlante = parlanti.trova(comando.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(comando.parlanteId))
        nomeValidato(comando.nome, progettoId = parlante.progettoId, escluso = parlante.id)
            .poi { nome -> parlante.promuovi(nome) }
            .poi { evento ->
                parlanti.salva(parlante).poi {
                    eventi.pubblica(evento.pubblicato())
                    Esito.Ok(Unit)
                }
            }
    }

    /** No rename requested (`null`) passes through; a given [nome] is validated then INV-16 pre-checked. */
    private fun nomeValidato(nome: String?, progettoId: ProgettoId, escluso: ParlanteId): Esito<Nome?> {
        if (nome == null) return Esito.Ok(null)
        return Nome.di(nome).poi { validato ->
            if (parlanti.nomeAttivoInUso(progettoId, validato, escluso)) {
                Esito.Errore(NomeGiaInUso(validato.valore))
            } else {
                Esito.Ok(validato)
            }
        }
    }
}

private fun ParlantePromosso.pubblicato(): ParlantePromossoPubblicato =
    ParlantePromossoPubblicato(parlanteId, nome, nomeCambiato)
