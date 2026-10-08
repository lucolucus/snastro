package snastro.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.ui.progetti.ConfermaEliminazione
import snastro.ui.testi.ETICHETTA_CHIUDI_PROGETTO
import snastro.ui.testi.ETICHETTA_ELIMINA_PROGETTO
import snastro.ui.testi.ETICHETTA_IMPOSTAZIONI
import snastro.ui.testi.ETICHETTA_TUTTO_IN_LOCALE
import kotlin.test.assertEquals

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)
private val PROGETTO_PROVA = ProgettoAperto(ProgettoId("id-1"), "Riunione team", "/tmp/riunione-team.snastro")
private val CON_PROGETTO = ShellUiStato.ConProgetto(PROGETTO_PROVA, OGNI_SEZIONE, DestinazioneShell.REGISTRAZIONI)
private val AZIONI_VUOTE = AzioniShell(
    apri = {},
    crea = { _, _ -> },
    chiudi = {},
    chiudiErrore = {},
    seleziona = {},
    chiediElimina = {},
    annullaElimina = {},
    confermaElimina = {},
)
private val CAMPO_NOME = hasSetTextAction() and hasAnyAncestor(hasTestTag("progetti-campo-nome-elimina"))

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
                stato = CON_PROGETTO,
                azioni = AZIONI_VUOTE.copy(chiudi = { chiusure++ }),
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

    /** Rework cycle 1-2 (HIGH #1/#9, MED #3): the footer never claims readiness it cannot back, and — when
     * the composition root wires it — IS the S5 navigation entry point and says so in visible text. */
    @Test
    fun `AC-572 il piede dice Modelli e licenze, Tutto in locale e naviga a S5`() = runDesktopComposeUiTest {
        var navigazioni = 0
        setContent {
            SchermataShell(
                stato = CON_PROGETTO,
                azioni = AZIONI_VUOTE,
                contenuto = { Text("Contenuto della sezione selezionata") },
                onImpostazioni = { navigazioni++ },
                riduciMovimento = true,
            )
        }

        onNodeWithText(ETICHETTA_TUTTO_IN_LOCALE).assertIsDisplayed()
        onNodeWithText("Modelli pronti", substring = true).assertDoesNotExist()
        onNodeWithText(ETICHETTA_IMPOSTAZIONI).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick()
        assertEquals(1, navigazioni)
    }

    @Test
    fun `EX-4 Elimina progetto nel menu dell intestazione chiede la conferma`() = runDesktopComposeUiTest {
        var chieste = 0
        setContent {
            SchermataShell(
                stato = CON_PROGETTO,
                azioni = AZIONI_VUOTE.copy(chiediElimina = { chieste++ }),
                riduciMovimento = true,
            )
        }

        onNodeWithTag("shell-altre-azioni").performClick()
        onNodeWithText(ETICHETTA_ELIMINA_PROGETTO).assertIsDisplayed()
        onNodeWithTag("shell-menu-elimina").performClick()

        assertEquals(1, chieste)
    }

    @Test
    fun `EX-4 EX-5 la conferma passa il nome digitato e mostra il rifiuto`() = runDesktopComposeUiTest(1280, 800) {
        var confermato: String? = null
        setContent {
            SchermataShell(
                stato = CON_PROGETTO.copy(
                    conferma = ConfermaEliminazione(PROGETTO_PROVA.nome, PROGETTO_PROVA.percorso, errore = "rifiutato"),
                ),
                azioni = AZIONI_VUOTE.copy(confermaElimina = { confermato = it }),
                riduciMovimento = true,
            )
        }

        onNodeWithTag("progetti-errore-elimina").assertTextEquals("rifiutato")
        onNode(CAMPO_NOME).performTextInput(PROGETTO_PROVA.nome)
        onNodeWithTag("progetti-conferma-elimina-conferma").performClick()

        assertEquals(PROGETTO_PROVA.nome, confermato)
    }
}
