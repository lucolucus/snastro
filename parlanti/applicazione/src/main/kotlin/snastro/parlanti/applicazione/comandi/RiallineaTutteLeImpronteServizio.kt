package snastro.parlanti.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.parlanti.applicazione.porte.ParlanteRepository

/**
 * Use-case `RiallineaTutteLeImpronte` (AC-300, ADR 0012 Amendment (b) point 3): at project open, after
 * `RecuperaElaborazioniInterrotte`, runs [RiallineaImpronteServizio] for every Registrazione of the Progetto
 * holding at least one print row. A failing Registrazione does not stop the others: once all ran, the first
 * [Esito.Errore] is returned, or the failures are rethrown as one exception naming every failed Registrazione
 * (fatal throwables — [Error], [InterruptedException] — propagate at once).
 */
public class RiallineaTutteLeImpronteServizio(
    private val uow: UnitaDiLavoro,
    private val parlanti: ParlanteRepository,
    private val riallinea: RiallineaImpronteServizio,
) {
    public fun esegui(c: RiallineaTutteLeImpronte): Esito<Unit> =
        uow.inTransazione { Esito.Ok(parlanti.impronteDelProgetto(c.progettoId)) }
            .poi { righe -> riallineaOgnuna(righe.map { it.voceRef.registrazioneId }.distinct()) }

    private fun riallineaOgnuna(ids: List<RegistrazioneId>): Esito<Unit> {
        var primoErrore: Esito.Errore? = null
        val fallimenti = ids.mapNotNull { id ->
            runCatching { riallinea.esegui(RiallineaImpronte(id)) }
                .onSuccess { if (it is Esito.Errore && primoErrore == null) primoErrore = it }
                .exceptionOrNull()
                ?.also { e -> if (e is Error || e is InterruptedException) throw e }
                ?.let { id to it }
        }
        if (fallimenti.isNotEmpty()) {
            val (_, primo) = fallimenti.first()
            throw IllegalStateException(
                "RiallineaTutteLeImpronte: fallito per le registrazioni ${fallimenti.map { it.first.valore }}",
                primo,
            ).apply { fallimenti.drop(1).forEach { addSuppressed(it.second) } }
        }
        return primoErrore ?: Esito.Ok(Unit)
    }
}
