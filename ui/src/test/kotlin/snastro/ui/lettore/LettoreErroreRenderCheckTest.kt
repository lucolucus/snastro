package snastro.ui.lettore

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * L471d render-check (`:ui:renderCheck`, profile `ui_render_check`): [LettoreUiStato.Errore] renders its
 * OWN caption (distinct tag from [LettoreUiStato.NonDisponibile]'s), and — unlike `NonDisponibile` — the
 * play control stays ENABLED, so presenter-green alone would not have proven this (a disabled control is
 * a rendering fact, not a presenter-state fact).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class LettoreErroreRenderCheckTest {
    @Test
    fun `L471d un errore transitorio mostra il messaggio con il pulsante riproduci ancora abilitato`() =
        runDesktopComposeUiTest(1280, 800) {
            val messaggio = "Guasto transitorio."
            setContent {
                BarraLettore(LettoreUiStato.Errore(messaggio), onRiproduci = {}, onPausa = {})
            }
            onNodeWithTag("lettore-riproduci").assertIsDisplayed().assertIsEnabled()
            onNodeWithTag("lettore-errore").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
        }
}
