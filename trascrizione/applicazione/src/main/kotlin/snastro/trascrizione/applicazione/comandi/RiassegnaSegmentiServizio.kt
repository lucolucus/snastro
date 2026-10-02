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
import snastro.trascrizione.dominio.SpostamentoNellIncontro

/**
 * Use-case `RiassegnaSegmenti` (AC-518/519/520, ADR 0019 §4.5): ONE transaction — `trova` →
 * [snastro.trascrizione.dominio.VociDellIncontro.riassegnaInBlocco] over the Segmenti of the Parte
 * [RiassegnaSegmenti.registrazioneId] (the root owns every rule and the stale guard, RC-1) → ONE `salva` → the N
 * `SegmentoRiassegnato` published in list order. The synchronous Parlanti revisione-policy runs once per event
 * inside the transaction; an `Esito.Errore` from it rolls the WHOLE batch back (ADR 0012).
 * An empty list is `Ok` without opening a transaction.
 */
public class RiassegnaSegmentiServizio(
    private val uow: UnitaDiLavoro,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiassegnaSegmenti): Esito<Unit> =
        if (c.spostamenti.isEmpty()) {
            Esito.Ok(Unit)
        } else {
            uow.inTransazione {
                val radice = trascritti.radiceDi(c.registrazioneId, registrazioni)
                    ?: return@inTransazione Esito.Errore(TrascrittoNonTrovato(c.registrazioneId))
                radice.vociDiQuestoIncontro(c.incontroDelleVoci, c.spostamenti.flatMap { listOf(it.da, it.a) })
                    ?.let { return@inTransazione it }
                val spostamenti = c.spostamenti.map {
                    SpostamentoNellIncontro(SegmentoRef(c.registrazioneId, it.segmentoId), it.da, it.a, it.intervallo)
                }
                radice.riassegnaInBlocco(spostamenti).poi { riassegnati ->
                    trascritti.salva(radice)
                    riassegnati.forEach { eventi.pubblica(it.pubblicato()) }
                    Esito.Ok(Unit)
                }
            }
        }
}
