package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `ConfermaSegmento` (AC-517, ADR 0019 §3): sets or revokes the `confermato` flag — INV-26 is owned by
 * [snastro.trascrizione.dominio.Trascritto.confermaSegmento] (RC-1) — in one transaction. On a change it saves the
 * Trascritto and publishes `SegmentoConfermato` once (after-commit subscribers only); a no-op writes and publishes
 * nothing.
 */
public class ConfermaSegmentoServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ConfermaSegmento): Esito<Unit> = uow.inTransazione {
        val trascritto = trascritti.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        trascritto.confermaSegmento(c.segmento, c.confermato).poi { evento ->
            if (evento != null) {
                trascritti.salva(trascritto)
                eventi.pubblica(evento.pubblicato())
            }
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.trascrizione.dominio.SegmentoConfermato.pubblicato(): SegmentoConfermato =
    SegmentoConfermato(registrazioneId, segmentoId, confermato)
