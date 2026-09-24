package snastro.ui.stile

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SOGLIA_TESTO = 4.5
private const val SOGLIA_MARCA = 3.0
private const val COSTANTE_LUMINANZA_LINEARE = 0.03928
private const val DIVISORE_LUMINANZA_LINEARE = 12.92
private const val OFFSET_GAMMA = 0.055
private const val DIVISORE_GAMMA = 1.055
private const val ESPONENTE_GAMMA = 2.4
private const val COEFFICIENTE_ROSSO = 0.2126
private const val COEFFICIENTE_VERDE = 0.7152
private const val COEFFICIENTE_BLU = 0.0722
private const val OFFSET_CONTRASTO = 0.05

/** WCAG relative luminance / contrast ratio (sRGB, no external dependency — a handful of lines). */
private fun canaleLineare(componente: Float): Double {
    val c = componente.toDouble()
    return if (c <= COSTANTE_LUMINANZA_LINEARE) {
        c / DIVISORE_LUMINANZA_LINEARE
    } else {
        ((c + OFFSET_GAMMA) / DIVISORE_GAMMA).pow(ESPONENTE_GAMMA)
    }
}

private fun luminanzaRelativa(colore: Color): Double =
    COEFFICIENTE_ROSSO * canaleLineare(colore.red) +
        COEFFICIENTE_VERDE * canaleLineare(colore.green) +
        COEFFICIENTE_BLU * canaleLineare(colore.blue)

private fun rapportoContrasto(a: Color, b: Color): Double {
    val l1 = luminanzaRelativa(a)
    val l2 = luminanzaRelativa(b)
    val chiaro = maxOf(l1, l2)
    val scuro = minOf(l1, l2)
    return (chiaro + OFFSET_CONTRASTO) / (scuro + OFFSET_CONTRASTO)
}

/**
 * AC-571: the contrast pairs listed in README §Colore, both themes — ink/inkMuted on surface+raised
 * ≥ 4.5, accentInk on surface/raised/accentSoft ≥ 4.5, onAccent on accent/accentHover ≥ 4.5,
 * lineStrong/focus/voci on surface+raised ≥ 3.
 */
class SnastroContrastoTest {
    private fun verificaTema(colori: SnastroColori, nomeTema: String) {
        val testo = listOf("ink" to colori.ink, "inkMuted" to colori.inkMuted)
        val fondi = listOf("surface" to colori.surface, "raised" to colori.raised)
        for ((nomeTesto, coloreTesto) in testo) {
            for ((nomeFondo, coloreFondo) in fondi) {
                val r = rapportoContrasto(coloreTesto, coloreFondo)
                assertTrue(r >= SOGLIA_TESTO, "$nomeTema: $nomeTesto su $nomeFondo = $r (< $SOGLIA_TESTO)")
            }
        }
        for ((nomeFondo, coloreFondo) in fondi + listOf("accentSoft" to colori.accentSoft)) {
            val r = rapportoContrasto(colori.accentInk, coloreFondo)
            assertTrue(r >= SOGLIA_TESTO, "$nomeTema: accentInk su $nomeFondo = $r (< $SOGLIA_TESTO)")
        }
        for ((nomeFondo, coloreFondo) in listOf("accent" to colori.accent, "accentHover" to colori.accentHover)) {
            val r = rapportoContrasto(colori.onAccent, coloreFondo)
            assertTrue(r >= SOGLIA_TESTO, "$nomeTema: onAccent su $nomeFondo = $r (< $SOGLIA_TESTO)")
        }
        val marche = listOf("lineStrong" to colori.lineStrong, "focus" to colori.focus) +
            colori.voci.mapIndexed { indice, colore -> "voce-${indice + 1}" to colore }
        for ((nomeMarca, coloreMarca) in marche) {
            for ((nomeFondo, coloreFondo) in fondi) {
                val r = rapportoContrasto(coloreMarca, coloreFondo)
                assertTrue(r >= SOGLIA_MARCA, "$nomeTema: $nomeMarca su $nomeFondo = $r (< $SOGLIA_MARCA)")
            }
        }
    }

    @Test
    fun `AC-571 ColoriChiari rispetta le coppie di contrasto del README`() = verificaTema(ColoriChiari, "chiaro")

    @Test
    fun `AC-571 ColoriScuri rispetta le coppie di contrasto del README`() = verificaTema(ColoriScuri, "scuro")
}
