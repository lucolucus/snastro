package snastro.ui.stile

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.ui.SnastroTema
import kotlin.test.assertEquals

/**
 * Rework cycle 1 (S2 verifier finding): `CampoNumeroPersone`'s Enter/"Done" must be wired to the same
 * command 'Trascrivi'/'Riprova'/'Ritrascrivi' starts on click — a regression from the pre-restyle
 * `OutlinedTextField`, which had its own `keyboardActions.onDone`.
 */
@OptIn(ExperimentalTestApi::class)
class CampoNumeroPersoneTest {
    @Test
    fun `l Invio nel campo invoca onInvio`() = runDesktopComposeUiTest {
        var chiamate = 0
        setContent {
            SnastroTema {
                CampoNumeroPersone(
                    valore = "4",
                    onValoreCambiato = {},
                    onInvio = { chiamate++ },
                    modifier = Modifier.testTag("campo"),
                )
            }
        }
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("campo")), useUnmergedTree = true)
            .performImeAction()
        assertEquals(1, chiamate)
    }

    @Test
    fun `senza onInvio l Invio non lancia alcuna eccezione`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                CampoNumeroPersone(valore = "4", onValoreCambiato = {}, modifier = Modifier.testTag("campo"))
            }
        }
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("campo")), useUnmergedTree = true)
            .performImeAction()
    }
}
