package snastro.ui.stile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import snastro.ui.SnastroTema
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import kotlin.test.Test
import kotlin.test.assertEquals

/** The inline error shared by S1, S2 and the Parlanti screen: its text, and `<tag>-chiudi` calls `onChiudi`. */
@OptIn(ExperimentalTestApi::class)
class MessaggioInlineErroreTest {
    @Test
    fun `mostra il messaggio e il tasto chiudi chiama onChiudi`() = runDesktopComposeUiTest {
        var chiusure = 0
        setContent {
            SnastroTema(riduciMovimento = true) {
                MessaggioInlineErrore("Qualcosa non va.", { chiusure++ }, "prova-errore")
            }
        }
        onNodeWithText("Qualcosa non va.").assertExists()
        onNodeWithTag("prova-errore-chiudi").assertTextEquals(ETICHETTA_CHIUDI_ERRORE).performClick()
        assertEquals(1, chiusure)
    }
}
