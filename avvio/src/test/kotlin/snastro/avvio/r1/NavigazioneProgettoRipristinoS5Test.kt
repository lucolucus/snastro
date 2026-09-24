package snastro.avvio.r1

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import snastro.kernel.RegistrazioneId
import snastro.ui.SessioneProgettoFinta
import snastro.ui.ShellPresenter

private const val S2 = "contenuto S2 elenco"
private const val S3 = "contenuto S3 registrazione"
private const val S4 = "contenuto S4 parlanti"
private const val S5 = "contenuto S5 modelli"

/**
 * L755e: opening S5 (the sidebar footer) from S3 and then leaving it sideways (a nav click into
 * Parlanti) restores S3 — the place S5 was opened FROM — not the list, on the SAME real composition
 * wiring [NavigazioneProgettoTest] itself uses ([ShellProgetto] + [ContenutoProgetto]).
 */
@OptIn(ExperimentalTestApi::class)
class NavigazioneProgettoRipristinoS5Test {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterEach
    fun chiudi() = scope.cancel()

    @Test
    fun `S5 aperto da S3 e lasciato verso Parlanti torna a S3, non alla lista`() = runDesktopComposeUiTest {
        val sessione = SessioneProgettoFinta().also { it.crea("/tmp", "Prova") }
        val shell = ShellPresenter(scope, Dispatchers.Unconfined, sessione, SEZIONI_SHELL_R2)
        setContent {
            ShellProgetto(
                shellPresenter = shell,
                iniziale = { SchermataR1.Registrazioni },
                contenutoSenzaProgetto = {},
                contenuto = { conProgetto, navigazione ->
                    ContenutoProgetto(
                        conProgetto = conProgetto,
                        navigazione = navigazione,
                        elenco = {
                            Text(
                                S2,
                                modifier = Modifier.testTag("vai-a-s3")
                                    .clickable { navigazione.apriRegistrazione(RegistrazioneId("id-1")) },
                            )
                        },
                        registrazione = { Text(S3) },
                        modelli = { Text(S5) },
                        parlanti = { Text(S4) },
                    )
                },
            )
        }

        onNodeWithTag("vai-a-s3").performClick()
        onNodeWithText(S3).assertIsDisplayed()

        onNodeWithTag("shell-piede").performClick() // apre S5 da S3
        onNodeWithText(S5).assertIsDisplayed()

        onNodeWithTag("shell-nav-parlanti").performClick() // lascia S5 di lato, verso Parlanti
        onNodeWithText(S4).assertIsDisplayed()

        onNodeWithTag("shell-nav-registrazioni").performClick() // torna alla sezione Registrazioni
        onNodeWithText(S3).assertIsDisplayed() // S3, non la lista S2 (L755e)
    }
}
