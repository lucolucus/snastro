package snastro.ui

import snastro.kernel.VoceId
import snastro.ui.stile.ColoriChiari
import snastro.ui.stile.ColoriScuri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaletteTest {
    @Test
    fun `AC-178 lo stesso numero di Voce restituisce sempre lo stesso colore`() {
        assertEquals(palette(VoceId(3), ColoriChiari), palette(VoceId(3), ColoriChiari))
        assertEquals(palette(VoceId(11), ColoriChiari), palette(VoceId(11), ColoriChiari))
    }

    @Test
    fun `AC-178 numeri di Voce diversi tendono a colori diversi`() {
        assertNotEquals(palette(VoceId(1), ColoriChiari), palette(VoceId(2), ColoriChiari))
    }

    @Test
    fun `AC-560 Voce 1 a 8 restituiscono i token voice-1 a voice-8 del tema attivo`() {
        for (n in 1..8) {
            assertEquals(ColoriChiari.voci[n - 1], palette(VoceId(n), ColoriChiari), "chiaro voce $n")
            assertEquals(ColoriScuri.voci[n - 1], palette(VoceId(n), ColoriScuri), "scuro voce $n")
        }
    }

    @Test
    fun `AC-560 Voce 9 ricomincia da voice-1`() {
        assertEquals(ColoriChiari.voci[0], palette(VoceId(9), ColoriChiari))
        assertEquals(ColoriScuri.voci[0], palette(VoceId(9), ColoriScuri))
    }
}
