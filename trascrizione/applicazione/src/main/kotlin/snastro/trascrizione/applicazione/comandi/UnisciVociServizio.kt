package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `UnisciVoci` (AC-76/77/82/83): merges [UnisciVoci.rimossa] into [UnisciVoci.sopravvive] on the
 * Trascritto of [UnisciVoci.registrazioneId] — INV-9 is owned by [snastro.trascrizione.dominio.Trascritto.unisci]
 * (RC-1) — saves it and publishes `VociUnite`. The Parlanti revisione-policy runs synchronously in the same
 * transaction (ADR 0012): its `Esito.Errore` rolls the whole command back.
 */
public class UnisciVociServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: UnisciVoci): Esito<Unit> = uow.inTransazione {
        val trascritto = trascritti.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        trascritto.unisci(c.sopravvive, c.rimossa).poi { evento ->
            trascritti.salva(trascritto)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}

private fun snastro.trascrizione.dominio.VociUnite.pubblicato(): VociUnite =
    VociUnite(registrazioneId, sopravvissuta, rimossa)
