package snastro.avvio.progetto

import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import snastro.avvio.SEZIONI_SHELL
import snastro.ui.SessioneProgettoFinta
import snastro.ui.ShellPresenter
import snastro.ui.modelli.ModelliPresenter
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelloFacoltativo

private const val S2 = "contenuto S2 elenco"
private const val S4 = "contenuto S4 parlanti"
private const val S5 = "contenuto S5 modelli"

/**
 * Rework cycle 2 (HIGH #1): the sidebar footer opens S5 from ANY section, and leaving S5 through a nav
 * item leaves it for good — on the REAL composition wiring ([ShellProgetto] + [ContenutoProgetto], the
 * same calls `ContenutoApp` makes) over a real [ShellPresenter], with stand-in screens.
 */
@OptIn(ExperimentalTestApi::class)
class NavigazioneProgettoTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterEach
    fun chiudi() = scope.cancel()

    @Test
    fun `da Parlanti il piede apre S5, poi Registrazioni torna all elenco`() = runDesktopComposeUiTest {
        componi()
        onNodeWithTag("shell-nav-parlanti").performClick()
        onNodeWithText(S4).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick()
        onNodeWithText(S5).assertIsDisplayed()
        onNodeWithText(S4).assertDoesNotExist()

        onNodeWithTag("shell-nav-registrazioni").performClick()
        onNodeWithText(S2).assertIsDisplayed()
        onNodeWithText(S5).assertDoesNotExist()
    }

    @Test
    fun `lasciare S5 verso Parlanti non lascia S5 nascosto dietro Registrazioni`() = runDesktopComposeUiTest {
        componi()
        onNodeWithTag("shell-piede").performClick()
        onNodeWithText(S5).assertIsDisplayed()

        onNodeWithTag("shell-nav-parlanti").performClick()
        onNodeWithText(S4).assertIsDisplayed()
        onNodeWithTag("shell-nav-registrazioni").performClick()
        onNodeWithText(S2).assertIsDisplayed()
    }

    @Test
    fun `il piede apre S5 e Registrazioni torna all elenco`() = runDesktopComposeUiTest {
        componi()
        onNodeWithText(S2).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick()
        onNodeWithText(S5).assertIsDisplayed()

        onNodeWithTag("shell-nav-registrazioni").performClick()
        onNodeWithText(S2).assertIsDisplayed()
    }

    private fun ComposeUiTest.componi() {
        val sessione = SessioneProgettoFinta().also { it.crea("/tmp", "Prova") }
        val shell = ShellPresenter(scope, Dispatchers.Unconfined, sessione, SEZIONI_SHELL)
        setContent {
            ShellProgetto(
                shellPresenter = shell,
                iniziale = { SchermataR1.Registrazioni },
                contenutoSenzaProgetto = {},
                contenuto = { conProgetto, navigazione ->
                    ContenutoProgetto(
                        conProgetto = conProgetto,
                        navigazione = navigazione,
                        elenco = { Text(S2) },
                        registrazione = { Text("S3") },
                        impostazioni = { Text(S5) },
                        parlanti = { Text(S4) },
                    )
                },
                etichettaModelloLinguisticoPiede = MutableStateFlow<String?>(null),
            )
        }
    }

    /**
     * AC-S163: `ShellRoute` receives THIS SAME `ModelliPresenter`'s flow — never a copy or a re-derived
     * one — proven by driving the underlying `ServizioModelliFinta` (the way `:avvio`'s real
     * `ServizioModelliProvisioning` would) and observing the shell's footer line follow it, on the
     * built [ShellProgetto] + [ContenutoProgetto] graph `ContenutoApp` itself uses.
     */
    @Test
    fun `AC-S163 il piede mostra l etichetta della STESSA ModelliPresenter, e la segue`() = runDesktopComposeUiTest {
        val servizio = ServizioModelliFinta()
        val modelliPresenter = ModelliPresenter(scope, Dispatchers.Unconfined, servizio)
        val sessione = SessioneProgettoFinta().also { it.crea("/tmp", "Prova") }
        val shell = ShellPresenter(scope, Dispatchers.Unconfined, sessione, SEZIONI_SHELL)
        setContent {
            ShellProgetto(
                shellPresenter = shell,
                iniziale = { SchermataR1.Registrazioni },
                contenutoSenzaProgetto = {},
                contenuto = { conProgetto, navigazione ->
                    ContenutoProgetto(
                        conProgetto = conProgetto,
                        navigazione = navigazione,
                        elenco = { Text(S2) },
                        registrazione = { Text("S3") },
                        impostazioni = { Text(S5) },
                        parlanti = { Text(S4) },
                    )
                },
                etichettaModelloLinguisticoPiede = modelliPresenter.etichettaModelloLinguisticoPiede,
            )
        }

        onNodeWithTag("shell-piede-modello-linguistico", useUnmergedTree = true).assertDoesNotExist()

        servizio.emettiFacoltativo("llm-prova", StatoModelloFacoltativo.InDownload(2_100_000_000, 6_200_000_000))
        waitForIdle()

        onNodeWithText("Modello di linguaggio: 2,1 di 6,2 GB").assertIsDisplayed()
    }
}
