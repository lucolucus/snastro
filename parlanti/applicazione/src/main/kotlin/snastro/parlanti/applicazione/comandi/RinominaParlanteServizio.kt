package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.parlanti.applicazione.comandi.ErroreApplicazioneParlanti.ParlanteNonTrovato
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.ErroreParlanti.NomeGiaInUso
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.ParlanteRinominato
import snastro.parlanti.applicazione.eventi.ParlanteRinominato as ParlanteRinominatoPubblicato

/**
 * `RinominaParlante` (AC-90/AC-91): goes through [snastro.parlanti.dominio.Parlante.rinomina] for
 * `[INV-13]` (an `eliminato` Parlante refuses every change); pre-checks `[INV-16]` (ADR 0007)
 * before touching the root — the repository's unique index is the backstop, chained so a
 * concurrent-insert violation is never silently dropped.
 */
public class RinominaParlanteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: RinominaParlante): Esito<Unit> = uow.inTransazione {
        val parlante = parlanti.trova(comando.parlanteId)
            ?: return@inTransazione Esito.Errore(ParlanteNonTrovato(comando.parlanteId))
        Nome.di(comando.nome)
            .poi { nome ->
                if (parlanti.nomeAttivoInUso(parlante.progettoId, nome, escluso = parlante.id)) {
                    Esito.Errore(NomeGiaInUso(nome.valore))
                } else {
                    Esito.Ok(nome)
                }
            }
            .poi { nome -> parlante.rinomina(nome) }
            .poi { evento ->
                parlanti.salva(parlante).poi {
                    eventi.pubblica(evento.pubblicato())
                    Esito.Ok(Unit)
                }
            }
    }
}

private fun ParlanteRinominato.pubblicato(): ParlanteRinominatoPubblicato =
    ParlanteRinominatoPubblicato(parlanteId, nome)
