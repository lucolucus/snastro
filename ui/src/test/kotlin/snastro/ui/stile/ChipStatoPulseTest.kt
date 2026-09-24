package snastro.ui.stile

import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * L714b: the AC-565 running pulse (`PallinoInCorso`'s `rememberInfiniteTransition`) was never
 * actually exercised by any test — a render-check PNG is a single still frame, and every fixture
 * pinned `riduciMovimento = true` on purpose (no animation, deterministic screenshot). Here the
 * clock is held (`mainClock.autoAdvance = false`) and stepped manually with `riduciMovimento =
 * false`, sampling the dot's own pixel to prove its alpha actually changes over time.
 */
@OptIn(ExperimentalTestApi::class)
class ChipStatoPulseTest {
    @Test
    fun `L714b il pallino anima l alpha nel tempo quando il movimento non e ridotto`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            SnastroTema(riduciMovimento = false) {
                Surface(color = ColoriChiari.ground) {
                    ChipStato(TipoChipStato.InCorso("Fase", 0))
                }
            }
        }

        fun colorePallino(): Int {
            val bounds = onNodeWithTag(TAG_PALLINO_IN_CORSO).fetchSemanticsNode().boundsInRoot
            val x = bounds.center.x.toInt()
            val y = bounds.center.y.toInt()
            return onRoot().captureToImage().toAwtImage().getRGB(x, y)
        }

        mainClock.advanceTimeByFrame()
        val primoColore = colorePallino()
        mainClock.advanceTimeBy(400)
        val secondoColore = colorePallino()

        assertNotEquals(primoColore, secondoColore, "l'alpha del pallino deve cambiare durante la pulsazione")
    }

    @Test
    fun `L714b con movimento ridotto il pallino resta un punto fermo (nessuna animazione)`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            SnastroTema(riduciMovimento = true) {
                Surface(color = ColoriChiari.ground) {
                    ChipStato(TipoChipStato.InCorso("Fase", 0))
                }
            }
        }

        fun colorePallino(): Int {
            val bounds = onNodeWithTag(TAG_PALLINO_IN_CORSO).fetchSemanticsNode().boundsInRoot
            val x = bounds.center.x.toInt()
            val y = bounds.center.y.toInt()
            return onRoot().captureToImage().toAwtImage().getRGB(x, y)
        }

        mainClock.advanceTimeByFrame()
        val primoColore = colorePallino()
        mainClock.advanceTimeBy(800)
        val secondoColore = colorePallino()

        assertEquals(primoColore, secondoColore, "il punto fermo non deve cambiare colore nel tempo")
    }
}
