package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceId
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `RiassegnaSegmento` (AC-80/81/82/83): moves [RiassegnaSegmento.segmento] to
 * [RiassegnaSegmento.destinazione] on the Trascritto of [RiassegnaSegmento.registrazioneId] — INV-11 is owned by
 * [snastro.trascrizione.dominio.Trascritto.riassegna] (RC-1), which also confirms the moved Segmento (INV-26) —
 * saves it, publishes `SegmentoRiassegnato` and returns the destination Voce (the NEW one when
 * [RiassegnaSegmento.destinazione] is `null`, ADR 0019 §5).
 * The Parlanti revisione-policy runs synchronously in the same transaction (ADR 0012): its `Esito.Errore` rolls
 * the whole command back.
 */
public class RiassegnaSegmentoServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiassegnaSegmento): Esito<VoceId> = uow.inTransazione {
        val trascritto = trascritti.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        trascritto.riassegna(c.segmento, c.destinazione).poi { evento ->
            trascritti.salva(trascritto)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(evento.a)
        }
    }
}

/** Domain → published `SegmentoRiassegnato`; shared with [RiassegnaSegmentiServizio]. */
internal fun snastro.trascrizione.dominio.SegmentoRiassegnato.pubblicato(): SegmentoRiassegnato =
    SegmentoRiassegnato(registrazioneId, segmentoId, da, a, daRimossa, aNuova)
