package snastro.avvio.r3

import snastro.avvio.porte.PorteProgetto
import snastro.sintesi.adattatori.persistenza.LunghezzaMassimaRiassuntoRepositorySql
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql

/**
 * Sintesi's part of [PorteProgetto] (ADR 0030 §1, AC-C60/AC-C61): its two SQL repositories, ONE instance each per
 * open project, built only through [sintesi] (the constructor is private). Declared here, not in
 * `snastro.avvio.porte`, so no Sintesi class is named outside `r3` (`GrafoR0Test` AC-350).
 */
internal class PorteProgettoSintesi private constructor(porte: PorteProgetto) {
    val riassunti: RiassuntoRepositorySql = RiassuntoRepositorySql(porte.database, porte.lettura)
    val lunghezze: LunghezzaMassimaRiassuntoRepositorySql = LunghezzaMassimaRiassuntoRepositorySql(porte.database)

    companion object {
        /** The project's Sintesi part: built on the first request, the same instance afterwards. */
        val PorteProgetto.sintesi: PorteProgettoSintesi
            get() = parte(PorteProgettoSintesi::class.java, ::PorteProgettoSintesi)
    }
}
