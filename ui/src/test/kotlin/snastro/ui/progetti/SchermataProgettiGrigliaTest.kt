package snastro.ui.progetti

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.letture.ProgettoVista
import java.time.Instant
import kotlin.test.assertEquals

private val AZIONI_VUOTE = AzioniProgetti(
    crea = { _, _ -> },
    apri = {},
    chiudiErroreCrea = {},
    chiudiErroreApri = {},
    riprova = {},
)

private val PROGETTO = ProgettoVista(
    progettoId = ProgettoId("id-1"),
    nome = "Consiglio comunale",
    percorso = "/progetti/Consiglio comunale.snastro",
    numRegistrazioni = 3,
    ultimaAttivita = Instant.parse("2026-09-23T10:15:30Z"),
)

/** The home grid: the "Nuovo progetto" card opens the panel, a project card opens it, the header's hooks fire. */
@OptIn(ExperimentalTestApi::class)
class SchermataProgettiGrigliaTest {
    @Test
    fun `la card Nuovo progetto apre il pannello, Annulla lo chiude`() = runDesktopComposeUiTest(1280, 800) {
        setContent {
            SchermataProgetti(ProgettiUiStato.Dati(emptyList()), AZIONI_VUOTE, "/tmp/x", SceltaCartellaFinta())
        }
        onNodeWithTag("progetti-pannello-nuovo").assertDoesNotExist()

        onNodeWithTag("progetti-nuovo").performClick()
        onNodeWithTag("progetti-pannello-nuovo").assertIsDisplayed()

        onNodeWithTag("progetti-annulla-nuovo").performClick()
        onNodeWithTag("progetti-pannello-nuovo").assertDoesNotExist()
    }

    @Test
    fun `Crea nel pannello passa cartella e nome`() = runDesktopComposeUiTest(1280, 800) {
        var creato: Pair<String, String>? = null
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(emptyList()),
                AZIONI_VUOTE.copy(crea = { c, n -> creato = c to n }),
                "/tmp/genitore",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-nuovo").performClick()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("progetti-campo-nome"))).performTextInput("Riunione")
        onNodeWithTag("progetti-crea").performClick()

        assertEquals("/tmp/genitore" to "Riunione", creato)
    }

    @Test
    fun `un errore di creazione tiene il pannello aperto`() = runDesktopComposeUiTest(1280, 800) {
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(emptyList(), erroreCrea = "no"),
                AZIONI_VUOTE,
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-pannello-nuovo").assertIsDisplayed()
        onNodeWithTag("progetti-errore-crea").assertIsDisplayed()
    }

    @Test
    fun `la card di un progetto lo apre`() = runDesktopComposeUiTest(1280, 800) {
        var aperto: String? = null
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(PROGETTO)),
                AZIONI_VUOTE.copy(apri = { aperto = it }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-riga-id-1").performClick()

        assertEquals(PROGETTO.percorso, aperto)
    }

    @Test
    fun `il bottone Impostazioni c e solo se collegato e lo richiama`() = runDesktopComposeUiTest(1280, 800) {
        var aperte = 0
        var collegato by mutableStateOf(false)
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(emptyList()),
                AZIONI_VUOTE,
                "/tmp/x",
                SceltaCartellaFinta(),
                onImpostazioni = if (collegato) ({ aperte++ }) else null,
            )
        }
        onNodeWithTag("progetti-impostazioni").assertDoesNotExist()

        collegato = true
        waitForIdle()
        onNodeWithTag("progetti-impostazioni").performClick()

        assertEquals(1, aperte)
    }
}
