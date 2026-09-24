package snastro.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.ui.testi.ETICHETTA_CHIUDI_PROGETTO
import snastro.ui.testi.ETICHETTA_MODELLI_E_LICENZE
import snastro.ui.testi.ETICHETTA_TUTTO_IN_LOCALE
import kotlin.test.assertEquals

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)
private val PROGETTO_PROVA = ProgettoAperto(ProgettoId("id-1"), "Riunione team", "/tmp/riunione-team.snastro")

/**
 * Rework cycle 1 (HIGH #2): "Chiudi progetto" is a dedicated `BottoneIcona Close` at the selector
 * row's end — clicking the project NAME must do nothing (it used to silently close the project);
 * only the Close control, with its own accessible name, does.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSelettoreProgettoTest {
    @Test
    fun `AC-572 il bottone Close chiude il progetto, il nome non fa nulla`() = runDesktopComposeUiTest {
        var chiusure = 0
        setContent {
            SchermataShell(
                stato = ShellUiStato.ConProgetto(PROGETTO_PROVA, OGNI_SEZIONE, DestinazioneShell.REGISTRAZIONI),
                azioni = AzioniShell(
                    apri = {},
                    crea = { _, _ -> },
                    chiudi = { chiusure++ },
                    chiudiErrore = {},
                    seleziona = {},
                ),
                contenuto = { Text("Contenuto della sezione selezionata") },
                riduciMovimento = true,
            )
        }

        onNodeWithText(PROGETTO_PROVA.nome).performClick()
        assertEquals(0, chiusure, "cliccare sul nome del progetto non deve chiuderlo")

        onNodeWithContentDescription(ETICHETTA_CHIUDI_PROGETTO).assertIsDisplayed()
        onNodeWithTag("shell-chiudi-progetto").performClick()
        assertEquals(1, chiusure)
    }

    /** Rework cycle 1 (HIGH #1/#9): the footer never claims readiness it cannot back, and — when the
     * composition root wires it — IS the S5 navigation entry point. */
    @Test
    fun `AC-572 il piede dice Tutto in locale e naviga a Modelli quando cablato`() = runDesktopComposeUiTest {
        var navigazioni = 0
        setContent {
            SchermataShell(
                stato = ShellUiStato.ConProgetto(PROGETTO_PROVA, OGNI_SEZIONE, DestinazioneShell.REGISTRAZIONI),
                azioni = AzioniShell(apri = {}, crea = { _, _ -> }, chiudi = {}, chiudiErrore = {}, seleziona = {}),
                contenuto = { Text("Contenuto della sezione selezionata") },
                onModelliELicenze = { navigazioni++ },
                riduciMovimento = true,
            )
        }

        onNodeWithText(ETICHETTA_TUTTO_IN_LOCALE).assertIsDisplayed()
        onNodeWithText("Modelli pronti", substring = true).assertDoesNotExist()
        onNodeWithContentDescription(ETICHETTA_MODELLI_E_LICENZE).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick()
        assertEquals(1, navigazioni)
    }
}
