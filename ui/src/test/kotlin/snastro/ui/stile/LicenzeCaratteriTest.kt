package snastro.ui.stile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-556: the S5 licences list gains the three fonts, in `LicenzaVista` shape. */
class LicenzeCaratteriTest {
    @Test
    fun `AC-556 LICENZE_CARATTERI porta le tre licenze OFL`() {
        assertEquals(3, LICENZE_CARATTERI.size)
        assertTrue(LICENZE_CARATTERI.all { it.licenza == "SIL OFL 1.1" })
        val nomiAttesi = setOf("Instrument Sans", "Source Serif 4", "JetBrains Mono")
        assertEquals(nomiAttesi, LICENZE_CARATTERI.map { it.nome }.toSet())
    }
}
