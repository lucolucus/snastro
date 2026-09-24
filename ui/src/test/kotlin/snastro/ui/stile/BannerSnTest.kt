package snastro.ui.stile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TESTO_LUNGHISSIMO =
    "Un messaggio molto lungo che, senza un limite di una riga, andrebbe sicuramente a capo piu' " +
        "volte dentro un banner stretto come questo, occupando parecchie righe di testo."

/** L708: AC-566 requires "one line of body text" — before the fix a long [testo] simply wrapped. */
@OptIn(ExperimentalTestApi::class)
class BannerSnTest {
    @Test
    fun `L708 il corpo del banner non cresce in altezza con un testo piu lungo (resta a una riga)`() =
        runDesktopComposeUiTest {
            setContent {
                SnastroTema(riduciMovimento = true) {
                    Column {
                        BannerSn(
                            TipoBanner.Info,
                            titolo = "Titolo",
                            testo = "breve",
                            modifier = Modifier.testTag("corto").width(220.dp),
                        )
                        BannerSn(
                            TipoBanner.Info,
                            titolo = "Titolo",
                            testo = TESTO_LUNGHISSIMO,
                            modifier = Modifier.testTag("lungo").width(220.dp),
                        )
                    }
                }
            }
            val altezzaCorto = onNodeWithTag("corto").fetchSemanticsNode().boundsInRoot.height
            val altezzaLungo = onNodeWithTag("lungo").fetchSemanticsNode().boundsInRoot.height
            assertEquals(altezzaCorto, altezzaLungo, "un testo piu' lungo non deve far crescere il banner")
        }
}
