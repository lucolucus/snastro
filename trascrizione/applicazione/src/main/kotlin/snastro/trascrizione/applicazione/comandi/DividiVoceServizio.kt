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
 * Use-case `DividiVoce` (AC-78/79/82/83): splits [DividiVoce.segmenti] of the Parte [DividiVoce.registrazioneId] off
 * [DividiVoce.origine] into a new Voce — INV-10 is owned by [snastro.trascrizione.dominio.VociDellIncontro.dividi]
 * (RC-1), which also fixes the order of `spostati` — saves the root and publishes `VoceDivisa`. The Parlanti
 * revisione-policy runs synchronously in the same transaction (ADR 0012): its `Esito.Errore` rolls the whole command
 * back.
 */
public class DividiVoceServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: DividiVoce): Esito<Unit> = uow.inTransazione {
        val radice = trascritti.radiceDi(c.registrazioneId, registrazioni)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        radice.vociDiQuestoIncontro(c.incontroDelleVoci, listOf(c.origine))?.let { return@inTransazione it }
        radice.dividi(c.origine, c.segmenti.mapTo(LinkedHashSet()) { SegmentoRef(c.registrazioneId, it) }).poi { e ->
            trascritti.salva(radice)
            eventi.pubblica(e.pubblicato())
            Esito.Ok(Unit)
        }
    }
}
