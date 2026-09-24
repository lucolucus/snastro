package snastro.ui.stile

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Interaction wiring the render-check's PNGs don't exercise: a click reaches the caller's lambda. */
@OptIn(ExperimentalTestApi::class)
class InterazioniStileTest {
    @Test
    fun `AC-562 BottoneSn abilitato inoltra il click`() = runDesktopComposeUiTest {
        var cliccato = false
        setContent {
            SnastroTema {
                BottoneSn("Trascrivi", onClick = { cliccato = true }, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("b").performClick()
        assertTrue(cliccato)
    }

    @Test
    fun `AC-562 BottoneSn disabilitato non inoltra il click`() = runDesktopComposeUiTest {
        var cliccato = false
        setContent {
            SnastroTema {
                BottoneSn(
                    "Trascrivi",
                    onClick = { cliccato = true },
                    abilitato = false,
                    modifier = Modifier.testTag("b"),
                )
            }
        }
        onNodeWithTag("b").assertIsNotEnabled()
        assertFalse(cliccato)
    }

    @Test
    fun `AC-563 BottoneIconaSn inoltra il click`() = runDesktopComposeUiTest {
        var cliccato = false
        setContent {
            SnastroTema {
                BottoneIconaSn(Icona.Trash, "Elimina", onClick = { cliccato = true }, modifier = Modifier.testTag("bi"))
            }
        }
        onNodeWithTag("bi").performClick()
        assertTrue(cliccato)
    }

    @Test
    fun `AC-564 BottonePlay inoltra il click`() = runDesktopComposeUiTest {
        var cliccato = false
        setContent {
            SnastroTema {
                BottonePlay(
                    inRiproduzione = false,
                    onClick = { cliccato = true },
                    grande = true,
                    modifier = Modifier.testTag("play"),
                )
            }
        }
        onNodeWithTag("play").performClick()
        assertTrue(cliccato)
    }

    // Review HIGH-1: M3's clickable `Surface` pads every target to a 48dp minimum touch size by
    // default (`minimumInteractiveComponentSize()`) — SnastroTema turns that off
    // (`LocalMinimumInteractiveComponentSize provides Dp.Unspecified`) so `Sn` components keep
    // their documented AC-562 heights instead of silently occupying 48dp of layout.
    @Test
    fun `AC-562 BottoneSn piccolo misura 28dp di altezza dentro SnastroTema`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                BottoneSn("Salta", onClick = {}, piccolo = true, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("b").assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun `AC-562 BottoneSn normale misura 34dp di altezza dentro SnastroTema`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                BottoneSn("Trascrivi", onClick = {}, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("b").assertHeightIsEqualTo(34.dp)
    }
}
