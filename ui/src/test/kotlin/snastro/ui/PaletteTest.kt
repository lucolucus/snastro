package snastro.ui

import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaletteTest {
    @Test
    fun `AC-178 lo stesso numero di Voce restituisce sempre lo stesso colore`() {
        assertEquals(palette(VoceId(3)), palette(VoceId(3)))
        assertEquals(palette(VoceId(11)), palette(VoceId(11)))
    }

    @Test
    fun `AC-178 numeri di Voce diversi tendono a colori diversi`() {
        assertNotEquals(palette(VoceId(1)), palette(VoceId(2)))
    }
}
