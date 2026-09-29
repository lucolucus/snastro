package snastro.avvio.progetto

import snastro.avvio.ArrestoProgetto
import snastro.avvio.Avviabile
import snastro.avvio.ModuloComposizione
import snastro.avvio.coda.CodaCondivisa

/**
 * One open project as [apriProgetto] composed it: its [porte], its typed [collaboratori], the shared [coda], the two
 * declared subscriber lists ([ordineSincroni], [ordineDopoCommit], AC-S143/AC-355) and what was started, in order
 * ([ordineAvvio]) — which [arresta] stops in reverse under ONE deadline (AC-C73).
 */
@Suppress("LongParameterList") // one parameter per part of the composed project
internal class ProgettoComposto(
    val porte: PorteProgetto,
    val collaboratori: CollaboratoriProgetto,
    val coda: CodaCondivisa,
    val ordineSincroni: List<ModuloComposizione>,
    val ordineDopoCommit: List<ModuloComposizione>,
    val ordineAvvio: List<Avviabile>,
    private val arresto: ArrestoProgetto,
) {
    /** See [ArrestoProgetto.arresta]: call it after the project scope was cancelled. */
    fun arresta(poi: () -> Unit): Boolean = arresto.arresta(ordineAvvio, poi)
}
