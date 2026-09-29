package snastro.ui.stile

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertTrue

/** AC-S43: [FonteChip] — named / unnamed rendering, the AC's own timecode examples, non-interactivity. */
@OptIn(ExperimentalTestApi::class)
class FonteChipTest {
    @Test
    fun `AC-S43 con nome mostra il pallino pieno, il nome e il timecode sotto l ora`() = runDesktopComposeUiTest {
        setContent { SnastroTema { FonteChip(voceId = 1, nome = "Marco", inizioMs = 65_000) } }
        onNodeWithText("Marco").assertIsDisplayed()
        onNodeWithText("1:05").assertIsDisplayed()
    }

    @Test
    fun `AC-S43 senza nome mostra Voce n e il timecode dall ora in su`() = runDesktopComposeUiTest {
        setContent { SnastroTema { FonteChip(voceId = 4, nome = null, inizioMs = 3_725_000) } }
        onNodeWithText("Voce 4").assertIsDisplayed()
        onNodeWithText("1:02:05").assertIsDisplayed()
    }

    @Test
    fun `AC-S43 non e interattivo, nessuna azione di click nella semantica`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                FonteChip(voceId = 1, nome = "Marco", inizioMs = 0, modifier = Modifier.testTag("fc"))
            }
        }
        onNodeWithTag("fc").assertHasNoClickAction()
    }

    // Pre-release finding #113 (rework): [FonteChip]'s own KDoc now says this control REQUIRES a
    // bounded-width ancestor — this is the regression that backs that requirement. A LazyRow hands
    // its items `Constraints.Infinity` on the scroll axis (the same shape a `horizontalScroll` row
    // would), which is exactly the unbounded case the KDoc warns against.
    @Test
    fun `AC-S43 sotto un antenato a larghezza illimitata il nome resta leggibile, non a larghezza zero`() =
        runDesktopComposeUiTest {
            setContent {
                SnastroTema {
                    LazyRow {
                        items(1) {
                            FonteChip(voceId = 1, nome = "Marco", inizioMs = 65_000, modifier = Modifier.testTag("fc"))
                        }
                    }
                }
            }
            val nome = onNodeWithText("Marco").getUnclippedBoundsInRoot()
            assertTrue(
                (nome.right - nome.left).value > 0f,
                "il nome e' stato schiacciato a larghezza zero sotto un LazyRow",
            )
        }
}
