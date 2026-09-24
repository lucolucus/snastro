package snastro.ui.stile

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** L705: label weight and the disabled look, as pure functions (no Compose test rule needed). */
class CampoSnTest {
    @Test
    fun `L705 l etichetta usa il peso Medium richiesto da AC-567, non il caption normale`() {
        assertEquals(FontWeight.Medium, etichettaCampoStile(SnastroTipografiaDefault).fontWeight)
    }

    @Test
    fun `L705 disabilitato mostra sfondo sunken e bordo e testo inkFaint`() {
        val colori = coloriCampo(ColoriChiari, abilitato = false, hasErrore = false)
        assertEquals(ColoriChiari.sunken, colori.sfondo)
        assertEquals(ColoriChiari.inkFaint, colori.bordo)
        assertEquals(ColoriChiari.inkFaint, colori.testo)
    }

    @Test
    fun `L705 abilitato senza errore mostra sfondo raised e bordo lineStrong`() {
        val colori = coloriCampo(ColoriChiari, abilitato = true, hasErrore = false)
        assertEquals(ColoriChiari.raised, colori.sfondo)
        assertEquals(ColoriChiari.lineStrong, colori.bordo)
        assertEquals(ColoriChiari.ink, colori.testo)
    }

    @Test
    fun `L705 l errore resta danger anche se il campo e abilitato`() {
        val colori = coloriCampo(ColoriChiari, abilitato = true, hasErrore = true)
        assertEquals(ColoriChiari.danger, colori.bordo)
    }
}
