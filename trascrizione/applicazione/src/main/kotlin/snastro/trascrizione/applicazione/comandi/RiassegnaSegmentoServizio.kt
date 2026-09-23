package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `RiassegnaSegmento` (AC-80/81/82/83): moves [RiassegnaSegmento.segmento] to
 * [RiassegnaSegmento.destinazione] on the Trascritto of [RiassegnaSegmento.registrazioneId] — INV-11 is owned by
 * [snastro.trascrizione.dominio.Trascritto.riassegna] (RC-1) — saves it and publishes `SegmentoRiassegnato`.
 * The Parlanti revisione-policy runs synchronously in the same transaction (ADR 0012): its `Esito.Errore` rolls
 * the whole command back.
 */
public class RiassegnaSegmentoServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiassegnaSegmento): Esito<Unit> = uow.inTransazione {
        val trascritto = trascritti.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        trascritto.riassegna(c.segmento, c.destinazione).poi { evento ->
            trascritti.salva(trascritto)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.trascrizione.dominio.SegmentoRiassegnato.pubblicato(): SegmentoRiassegnato =
    SegmentoRiassegnato(registrazioneId, segmentoId, da, a, daRimossa, aNuova)
