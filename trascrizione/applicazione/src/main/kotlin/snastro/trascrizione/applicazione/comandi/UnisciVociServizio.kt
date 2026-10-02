package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.radiceDi
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `UnisciVoci` (AC-76/77/82/83): merges [UnisciVoci.rimossa] into [UnisciVoci.sopravvive] on the Voci
 * dell'Incontro of the Parte [UnisciVoci.registrazioneId] — INV-9 is owned by
 * [snastro.trascrizione.dominio.VociDellIncontro.unisci] (RC-1) — saves the root and publishes `VociUnite`. The
 * Parlanti revisione-policy runs synchronously in the same transaction (ADR 0012): its `Esito.Errore` rolls the whole
 * command back.
 */
public class UnisciVociServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: UnisciVoci): Esito<Unit> = uow.inTransazione {
        val radice = trascritti.radiceDi(c.registrazioneId, registrazioni)
            ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
        radice.unisci(c.sopravvive, c.rimossa).poi { evento ->
            trascritti.salva(radice)
            eventi.pubblica(evento.pubblicato())
            Esito.Ok(Unit)
        }
    }
}
