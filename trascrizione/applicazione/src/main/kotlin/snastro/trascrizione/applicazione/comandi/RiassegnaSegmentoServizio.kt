package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceId
import snastro.kernel.poi
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.radiceDi
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `RiassegnaSegmento` (AC-80/81/82/83): moves [RiassegnaSegmento.segmento] of the Parte
 * [RiassegnaSegmento.registrazioneId] to [RiassegnaSegmento.destinazione] — INV-11 is owned by
 * [snastro.trascrizione.dominio.VociDellIncontro.riassegna] (RC-1), which also confirms the moved Segmento (INV-26) —
 * saves the root, publishes `SegmentoRiassegnato` and returns the destination Voce (the NEW one when
 * [RiassegnaSegmento.destinazione] is `null`, ADR 0019 §5).
 * The Parlanti revisione-policy runs synchronously in the same transaction (ADR 0012): its `Esito.Errore` rolls
 * the whole command back.
 */
public class RiassegnaSegmentoServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiassegnaSegmento): Esito<VoceId> = uow.inTransazione {
        val radice = trascritti.radiceDi(c.registrazioneId, registrazioni)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        val rif = SegmentoRef(c.registrazioneId, c.segmento)
        radice.segmentoDiQuestoIncontro(c.incontroDelleVoci, rif)?.let { return@inTransazione it }
        radice.riassegna(rif, c.destinazione).poi { evento ->
            trascritti.salva(radice)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(evento.a)
        }
    }
}
