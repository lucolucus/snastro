package snastro.ui.stile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.ui.SnastroTema
import kotlin.test.Test

/** L735d: an icon-only play/pause control needs an accessible name — a screen reader has nothing
 * else to announce. */
@OptIn(ExperimentalTestApi::class)
class BottonePlayTest {
    @Test
    fun `L735d in pausa il nome accessibile e Riproduci`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema { BottonePlay(inRiproduzione = false, onClick = {}, grande = true) }
        }
        onNode(hasContentDescription("Riproduci")).assertIsDisplayed()
    }

    @Test
    fun `L735d in riproduzione il nome accessibile e Pausa`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema { BottonePlay(inRiproduzione = true, onClick = {}, grande = true) }
        }
        onNode(hasContentDescription("Pausa")).assertIsDisplayed()
    }
}
