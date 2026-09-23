package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `DividiVoce` (AC-78/79/82/83): splits [DividiVoce.segmenti] off [DividiVoce.origine] into a new Voce
 * on the Trascritto of [DividiVoce.registrazioneId] — INV-10 is owned by
 * [snastro.trascrizione.dominio.Trascritto.dividi] (RC-1), which also fixes `segmentiSpostati`'s order — saves it
 * and publishes `VoceDivisa`. The Parlanti revisione-policy runs synchronously in the same transaction
 * (ADR 0012): its `Esito.Errore` rolls the whole command back.
 */
public class DividiVoceServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: DividiVoce): Esito<Unit> = uow.inTransazione {
        val trascritto = trascritti.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        trascritto.dividi(c.origine, c.segmenti).poi { evento ->
            trascritti.salva(trascritto)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.trascrizione.dominio.VoceDivisa.pubblicato(): VoceDivisa =
    VoceDivisa(registrazioneId, origine, nuova, segmentiSpostati)
