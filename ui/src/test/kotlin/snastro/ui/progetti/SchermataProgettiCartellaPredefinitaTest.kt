package snastro.ui.progetti

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test

private val AZIONI_VUOTE = AzioniProgetti(crea = { _, _ -> }, apri = {}, chiudiErroreCrea = {}, chiudiErroreApri = {})

/**
 * fix-batch-12 #4 (ADR 0010): S1's default parent folder for a new project comes from
 * [SchermataProgetti]'s own injected `cartellaGenitorePredefinita` param — never `System.getProperty`
 * inside the composable — so a caller (here, the test itself) fully controls the initial value shown.
 */
@OptIn(ExperimentalTestApi::class)
class SchermataProgettiCartellaPredefinitaTest {
    @Test
    fun `il campo cartella parte dal valore iniettato da avvio, non da System getProperty`() =
        runDesktopComposeUiTest(width = 1280, height = 800) {
            val predefinita = "/una/cartella/iniettata/da/avvio"
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = predefinita,
                )
            }

            onNodeWithTag("progetti-cartella").assertTextEquals(predefinita)
        }
}
