package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato

/**
 * Use-case `RiassegnaSegmenti` (AC-518/519/520, ADR 0019 §4.5): ONE transaction — `trova` →
 * [snastro.trascrizione.dominio.Trascritto.riassegnaInBlocco] (the root owns every rule and the stale guard, RC-1)
 * → ONE `salva` → the N `SegmentoRiassegnato` published in list order. The synchronous Parlanti revisione-policy
 * runs once per event inside the transaction; an `Esito.Errore` from it rolls the WHOLE batch back (ADR 0012).
 * An empty list is `Ok` without opening a transaction.
 */
public class RiassegnaSegmentiServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: TrascrittoRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiassegnaSegmenti): Esito<Unit> =
        if (c.spostamenti.isEmpty()) {
            Esito.Ok(Unit)
        } else {
            uow.inTransazione {
                val trascritto = trascritti.trova(c.registrazioneId)
                    ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
                trascritto.riassegnaInBlocco(c.spostamenti).poi { riassegnati ->
                    trascritti.salva(trascritto)
                    riassegnati.forEach { eventi.pubblica(it.pubblicato()) }
                    Esito.Ok(Unit)
                }
            }
        }
}
