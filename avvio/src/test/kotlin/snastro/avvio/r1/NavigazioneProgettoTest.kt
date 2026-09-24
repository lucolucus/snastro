package snastro.avvio.r1

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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import snastro.avvio.r2.SEZIONI_SHELL_R2
import snastro.ui.DestinazioneShell
import snastro.ui.SessioneProgettoFinta
import snastro.ui.ShellPresenter

private const val S2 = "contenuto S2 elenco"
private const val S4 = "contenuto S4 parlanti"
private const val S5 = "contenuto S5 modelli"

/**
 * Rework cycle 2 (HIGH #1): the sidebar footer opens S5 from ANY section, and leaving S5 through a nav
 * item leaves it for good — on the REAL composition wiring ([ShellProgetto] + [ContenutoProgetto], the
 * same calls `ContenutoAppR1`/`ContenutoAppR2` make) over a real [ShellPresenter], with stand-in screens.
 */
@OptIn(ExperimentalTestApi::class)
class NavigazioneProgettoTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterEach
    fun chiudi() = scope.cancel()

    @Test
    fun `R2 da Parlanti il piede apre S5, poi Registrazioni torna all elenco`() = runDesktopComposeUiTest {
        componi(SEZIONI_SHELL_R2, conParlanti = true)
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
    fun `R2 lasciare S5 verso Parlanti non lascia S5 nascosto dietro Registrazioni`() = runDesktopComposeUiTest {
        componi(SEZIONI_SHELL_R2, conParlanti = true)
        onNodeWithTag("shell-piede").performClick()
        onNodeWithText(S5).assertIsDisplayed()

        onNodeWithTag("shell-nav-parlanti").performClick()
        onNodeWithText(S4).assertIsDisplayed()
        onNodeWithTag("shell-nav-registrazioni").performClick()
        onNodeWithText(S2).assertIsDisplayed()
    }

    @Test
    fun `R1 il piede apre S5 e Registrazioni torna all elenco`() = runDesktopComposeUiTest {
        componi(SEZIONI_SHELL_R1, conParlanti = false)
        onNodeWithText(S2).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick()
        onNodeWithText(S5).assertIsDisplayed()

        onNodeWithTag("shell-nav-registrazioni").performClick()
        onNodeWithText(S2).assertIsDisplayed()
    }

    private fun ComposeUiTest.componi(sezioni: Set<DestinazioneShell>, conParlanti: Boolean) {
        val sessione = SessioneProgettoFinta().also { it.crea("/tmp", "Prova") }
        val shell = ShellPresenter(scope, Dispatchers.Unconfined, sessione, sezioni)
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
                        modelli = { Text(S5) },
                        parlanti = if (conParlanti) ({ Text(S4) }) else null,
                    )
                },
            )
        }
    }
}
