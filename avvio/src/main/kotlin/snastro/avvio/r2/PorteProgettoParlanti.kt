package snastro.avvio.r2

import snastro.avvio.porte.PorteProgetto
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql

/**
 * Parlanti's part of [PorteProgetto] (ADR 0030 §1, AC-C60/AC-C61): its two SQL repositories, ONE instance each per
 * open project. Built only through [parlanti] (the constructor is private), so R1's Documento names
 * ([lettoreNomiDaParlanti], called inside R1's `apri`), R2 and R3 all receive the same two instances. Declared here,
 * not in `snastro.avvio.porte`, so no Parlanti class is named outside `r2`/`r3` (`CablaggioR1Test` AC-356).
 */
internal class PorteProgettoParlanti private constructor(porte: PorteProgetto) {
    val parlanti: ParlanteRepositorySql = ParlanteRepositorySql(porte.database, porte.lettura)
    val attribuzioni: AttribuzioneRepositorySql = AttribuzioneRepositorySql(porte.database)

    companion object {
        /** The project's Parlanti part: built on the first request, the same instance afterwards. */
        val PorteProgetto.parlanti: PorteProgettoParlanti
            get() = parte(PorteProgettoParlanti::class.java, ::PorteProgettoParlanti)
    }
}
