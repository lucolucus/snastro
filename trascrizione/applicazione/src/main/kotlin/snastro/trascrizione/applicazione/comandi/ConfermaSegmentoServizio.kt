package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.radiceDi
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `ConfermaSegmento` (AC-517, ADR 0019 §3): sets or revokes the `confermato` flag of a Segmento of the Parte
 * [ConfermaSegmento.registrazioneId] — INV-26 is owned by
 * [snastro.trascrizione.dominio.VociDellIncontro.confermaSegmento]
 * (RC-1) — in one transaction. On a change it saves the root and publishes `SegmentoConfermato` once (after-commit
 * subscribers only); a no-op writes and publishes nothing.
 */
public class ConfermaSegmentoServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: ConfermaSegmento): Esito<Unit> = uow.inTransazione {
        val radice = trascritti.radiceDi(c.registrazioneId, registrazioni)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        val rif = SegmentoRef(c.registrazioneId, c.segmento)
        radice.segmentoDiQuestoIncontro(c.incontroDelleVoci, rif)?.let { return@inTransazione it }
        radice.confermaSegmento(rif, c.confermato).poi { evento ->
            if (evento != null) {
                trascritti.salva(radice)
                eventi.pubblica(evento.pubblicato())
            }
            Esito.Ok(Unit)
        }
    }
}
