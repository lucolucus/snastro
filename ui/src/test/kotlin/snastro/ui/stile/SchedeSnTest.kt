package snastro.ui.stile

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
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
import kotlin.test.assertNull

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

    // Pre-release finding #114 (rework, MED): the mark was color/shape only, silent to a screen
    // reader — [SchedeSn] now attaches a `stateDescription` to the tab's own semantics.
    @Test
    fun `AC-S45 rework il segno InAttesa e annunciato come in coda dallo screen reader`() = runDesktopComposeUiTest {
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
        val nodo = onNodeWithTag("scheda-0").fetchSemanticsNode()
        assertEquals("in coda", nodo.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun `AC-S45 rework il segno InCorso e annunciato come in corso dallo screen reader`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(
                    schede = listOf("Trascrizione", "Riassunto"),
                    selezionata = 1,
                    onSeleziona = {},
                    segni = mapOf(1 to SegnoScheda.InCorso),
                )
            }
        }
        val nodo = onNodeWithTag("scheda-1").fetchSemanticsNode()
        assertEquals("in corso", nodo.config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun `AC-S45 rework senza segno non c e alcuna stateDescription`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(schede = listOf("Trascrizione", "Riassunto"), selezionata = 0, onSeleziona = {})
            }
        }
        val nodo = onNodeWithTag("scheda-0").fetchSemanticsNode()
        assertNull(nodo.config.getOrNull(SemanticsProperties.StateDescription))
    }

    // Pre-release finding #118 (rework, LOW): the InCorso pulse must respect reduce-motion — the
    // existing test above only proved it DOES pulse when motion is allowed; this is its mirror.
    @Test
    fun `AC-S45 rework il segno InCorso non pulsa quando il movimento e ridotto`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            SnastroTema(riduciMovimento = true) {
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
        assertEquals(primo, secondo, "il segno InCorso non deve pulsare quando il movimento e ridotto")
    }

    // Pre-release finding #116 (rework, LOW): a single tab has nothing to switch to — it must not
    // be a keyboard focus stop (the Voci panel's own one-tab header, AC-S45's non-AC caller). Not
    // focusable at all (no `RequestFocus` semantics action) — `requestFocus()` itself would throw.
    @Test
    fun `AC-116 rework una sola scheda non e a fuoco da tastiera ne cliccabile`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                SchedeSn(schede = listOf("Voci"), selezionata = 0, onSeleziona = {})
            }
        }
        onNodeWithTag("scheda-0").assertHasNoClickAction()
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
