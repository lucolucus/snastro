package snastro.ui.progetti

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.testi.ETICHETTA_CHIUDI
import java.time.Instant
import kotlin.test.assertEquals

private val TEST = ProgettoVista(
    progettoId = ProgettoId("id-test"),
    nome = "test",
    percorso = "/Users/me/Documents/snastro/test.snastro",
    numRegistrazioni = 0,
    ultimaAttivita = Instant.parse("2026-10-08T10:00:00Z"),
)

private val CAMPO_NOME = hasSetTextAction() and hasAnyAncestor(hasTestTag("progetti-campo-nome-elimina"))

/** R0 smoke (EX-1..EX-3): the card's "⋯" menu and the confirmation reach the presenter's delete actions. */
@OptIn(ExperimentalTestApi::class)
class SchermataProgettiEliminaTest {
    @Test
    fun `Elimina nel menu della scheda chiede la conferma di quel progetto`() = runDesktopComposeUiTest(1280, 800) {
        var chiesto: ProgettoVista? = null
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST)),
                AZIONI_VUOTE.copy(chiediElimina = { chiesto = it }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-altre-azioni-id-test").performClick()
        onNodeWithTag("progetti-menu-elimina-id-test").performClick()

        assertEquals(TEST, chiesto)
    }

    @Test
    fun `Elimina si attiva solo col nome esatto e passa il nome digitato`() = runDesktopComposeUiTest(1280, 800) {
        var confermato: String? = null
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST), conferma = ConfermaEliminazione(TEST)),
                AZIONI_VUOTE.copy(confermaElimina = { confermato = it }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-conferma-elimina").assertIsDisplayed()
        onNode(CAMPO_NOME).performTextInput("tes")
        onNodeWithTag("progetti-conferma-elimina-conferma").assertIsNotEnabled()

        onNode(CAMPO_NOME).performTextInput("t")
        onNodeWithTag("progetti-conferma-elimina-conferma").assertIsEnabled().performClick()

        assertEquals("test", confermato)
    }

    @Test
    fun `Annulla nella conferma la annulla`() = runDesktopComposeUiTest(1280, 800) {
        var annullate = 0
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST), conferma = ConfermaEliminazione(TEST)),
                AZIONI_VUOTE.copy(annullaElimina = { annullate++ }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-conferma-elimina-annulla").performClick()

        assertEquals(1, annullate)
    }

    @Test
    fun `un eliminazione fallita mostra il suo messaggio nella conferma`() = runDesktopComposeUiTest(1280, 800) {
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST), conferma = ConfermaEliminazione(TEST, errore = "non riuscita")),
                AZIONI_VUOTE,
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-errore-elimina").assertIsDisplayed()
    }

    @Test
    fun `EX-9 l avviso di cio che resta si vede e Chiudi lo chiude`() = runDesktopComposeUiTest(1280, 800) {
        var chiusi = 0
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(emptyList(), avvisoEliminazione = "Il progetto è stato eliminato."),
                AZIONI_VUOTE.copy(chiudiAvvisoEliminazione = { chiusi++ }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-avviso-eliminazione").assertIsDisplayed()
        onNodeWithText("Il progetto è stato eliminato.").assertIsDisplayed()
        onNodeWithText(ETICHETTA_CHIUDI).performClick()

        assertEquals(1, chiusi)
    }
}
