package snastro.ui.progetti

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.letture.ProgettoVista
import java.time.Instant
import kotlin.test.assertEquals

private val TEST_NON_TROVATO = ProgettoVista(
    progettoId = ProgettoId("id-test"),
    nome = "test",
    percorso = "/Users/me/Documents/snastro/test.snastro",
    numRegistrazioni = 0,
    ultimaAttivita = Instant.parse("2026-10-08T10:00:00Z"),
    cartellaTrovata = false,
)

/** R0 smoke (EX-7, EX-8, EX-13): a not-found project's card reaches the presenter's "Togli dalla lista" and `apri`. */
@OptIn(ExperimentalTestApi::class)
class SchermataProgettiNonTrovatoTest {
    @Test
    fun `la scheda di un progetto non trovato e segnata e il suo menu toglie dalla lista, senza Elimina`() =
        runDesktopComposeUiTest(1280, 800) {
            var tolto: ProgettoVista? = null
            setContent {
                SchermataProgetti(
                    ProgettiUiStato.Dati(listOf(TEST_NON_TROVATO)),
                    AZIONI_VUOTE.copy(togliDallaLista = { tolto = it }),
                    "/tmp/x",
                    SceltaCartellaFinta(),
                )
            }
            onNodeWithText("Cartella non trovata").assertIsDisplayed()
            onNodeWithTag("progetti-altre-azioni-id-test").performClick()
            onNodeWithTag("progetti-menu-elimina-id-test").assertDoesNotExist()
            onNodeWithTag("progetti-menu-togli-id-test").assertIsDisplayed()
            onNodeWithText("Togli dalla lista").performClick()

            assertEquals(TEST_NON_TROVATO, tolto)
        }

    @Test
    fun `un clic sulla scheda di un progetto non trovato prova ad aprirlo`() = runDesktopComposeUiTest(1280, 800) {
        var aperto: String? = null
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST_NON_TROVATO)),
                AZIONI_VUOTE.copy(apri = { aperto = it }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-riga-id-test").performClick()

        assertEquals(TEST_NON_TROVATO.percorso, aperto)
    }

    @Test
    fun `un Togli dalla lista fallito mostra il suo messaggio, che si chiude`() = runDesktopComposeUiTest(1280, 800) {
        var chiusi = 0
        setContent {
            SchermataProgetti(
                ProgettiUiStato.Dati(listOf(TEST_NON_TROVATO), erroreTogli = "non riuscito"),
                AZIONI_VUOTE.copy(chiudiErroreTogli = { chiusi++ }),
                "/tmp/x",
                SceltaCartellaFinta(),
            )
        }
        onNodeWithTag("progetti-errore-togli").assertIsDisplayed()
        onNodeWithTag("progetti-errore-togli-chiudi").performClick()

        assertEquals(1, chiusi)
    }
}
