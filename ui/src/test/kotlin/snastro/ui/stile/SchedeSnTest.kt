package snastro.ui.stile

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** AC-S45: [SchedeSn] — selection state, click forwarding, focus ring, the optional [SegnoScheda] mark. */
@OptIn(ExperimentalTestApi::class)
class SchedeSnTest {
    @Test
    fun `AC-S45 la scheda selezionata e le altre no, come vero stato Tab`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(schede = listOf("Trascrizione", "Riassunto"), selezionata = 1, onSeleziona = {})
            }
        }
        onNodeWithTag("scheda-0").assertIsNotSelected()
        onNodeWithTag("scheda-1").assertIsSelected()
        onNodeWithText("Trascrizione").assertIsDisplayed()
        onNodeWithText("Riassunto").assertIsDisplayed()
    }

    @Test
    fun `AC-S45 il clic su una scheda chiama onSeleziona con il suo indice`() = runDesktopComposeUiTest {
        var scelta: Int? = null
        setContent {
            SnastroTema {
                SchedeSn(
                    schede = listOf("Trascrizione", "Riassunto"),
                    selezionata = 0,
                    onSeleziona = { scelta = it },
                )
            }
        }
        onNodeWithTag("scheda-1").performClick()
        assertEquals(1, scelta)
    }

    @Test
    fun `AC-S45 il segno InAttesa mostra l icona clock`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(
                    schede = listOf("Riassunto"),
                    selezionata = 0,
                    onSeleziona = {},
                    segni = mapOf(0 to SegnoScheda.InAttesa),
                )
            }
        }
        // The tab's own `selectable` merges its descendants' semantics (like CampoSn's label+input):
        // the mark's own testTag survives only in the unmerged tree.
        onNodeWithTag("scheda-0-segno", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `AC-S45 il segno InCorso pulsa quando il movimento non e ridotto`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            SnastroTema(riduciMovimento = false) {
                SchedeSn(
                    schede = listOf("Riassunto"),
                    selezionata = 0,
                    onSeleziona = {},
                    segni = mapOf(0 to SegnoScheda.InCorso),
                )
            }
        }

        fun colore(): Int {
            val bounds = onNodeWithTag("scheda-0-segno", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val x = bounds.center.x.toInt()
            val y = bounds.center.y.toInt()
            return onRoot().captureToImage().toAwtImage().getRGB(x, y)
        }

        mainClock.advanceTimeByFrame()
        val primo = colore()
        mainClock.advanceTimeBy(400)
        val secondo = colore()
        assertNotEquals(primo, secondo, "il segno InCorso deve pulsare quando il movimento non e ridotto")
    }

    @Test
    fun `AC-570 la scheda a fuoco da tastiera riceve davvero il focus`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(schede = listOf("Trascrizione", "Riassunto"), selezionata = 0, onSeleziona = {})
            }
        }
        onNodeWithTag("scheda-1").requestFocus()
        onNodeWithTag("scheda-1").assertIsFocused()
        onNodeWithTag("scheda-0").assertIsNotFocused()
    }
}
