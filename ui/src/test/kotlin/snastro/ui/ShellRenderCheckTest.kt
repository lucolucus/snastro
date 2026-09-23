package snastro.ui

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import java.io.File
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val OGNI_SEZIONE = setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)
private val PROGETTO_PROVA = ProgettoAperto(ProgettoId("id-1"), "Riunione team", "/tmp/riunione-team.snastro")
private val AZIONI_VUOTE = AzioniShell(apri = {}, crea = { _, _ -> }, chiudi = {}, chiudiErrore = {}, seleziona = {})

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [ShellUiStato] fixture at both sizes —
 * sizing/overflow/contrast/state-rendering (AC-181), plus the R0 vs full-section shell (AC-341).
 * `SchermataShell` renders directly from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class ShellRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-177 senza progetto mostra solo S1 a 1280x800`() =
        verificaSenzaProgetto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-177 senza progetto mostra solo S1 a 1024x640`() =
        verificaSenzaProgetto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-181 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-181 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-181 errore di apertura senza progetto mostra un banner su S1 a 1280x800`() =
        verificaErroreSenzaProgetto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-181 errore di apertura senza progetto mostra un banner su S1 a 1024x640`() =
        verificaErroreSenzaProgetto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `H1 errore di apertura con un progetto aperto mostra un banner e mantiene la nav a 1280x800`() =
        verificaErroreConProgetto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `H1 errore di apertura con un progetto aperto mostra un banner e mantiene la nav a 1024x640`() =
        verificaErroreConProgetto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-341 R0 senza la sezione Parlanti nasconde la voce Parlanti a 1280x800`() =
        verificaConProgetto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, conParlanti = false)

    @Test
    fun `AC-341 R0 senza la sezione Parlanti nasconde la voce Parlanti a 1024x640`() =
        verificaConProgetto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, conParlanti = false)

    @Test
    fun `AC-177 con ogni sezione mostra Registrazioni e Parlanti a 1280x800`() =
        verificaConProgetto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, conParlanti = true)

    @Test
    fun `AC-177 con ogni sezione mostra Registrazioni e Parlanti a 1024x640`() =
        verificaConProgetto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, conParlanti = true)

    private fun verificaSenzaProgetto(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataShell(
                stato = ShellUiStato.SenzaProgetto(),
                azioni = AZIONI_VUOTE,
                contenutoSenzaProgetto = { Text("S1 - elenco progetti") },
            )
        }
        onNodeWithText("S1 - elenco progetti").assertIsDisplayed()
        onNodeWithText("Registrazioni").assertDoesNotExist()
        onNodeWithText("Parlanti").assertDoesNotExist()
        catturaPng("shell-senza-progetto", width, height)
    }

    private fun verificaCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { SchermataShell(stato = ShellUiStato.Caricamento, azioni = AZIONI_VUOTE) }
        onNodeWithTag("shell-indicatore-caricamento").assertIsDisplayed()
        catturaPng("shell-caricamento", width, height)
    }

    // H1: the error overlays S1 (a dismissible banner), it does not replace it.
    private fun verificaErroreSenzaProgetto(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val messaggio = "La cartella scelta non è valida."
        setContent {
            SchermataShell(
                stato = ShellUiStato.SenzaProgetto(erroreApertura = messaggio),
                azioni = AZIONI_VUOTE,
                contenutoSenzaProgetto = { Text("S1 - elenco progetti") },
            )
        }
        onNodeWithText("S1 - elenco progetti").assertIsDisplayed()
        onNodeWithTag("shell-errore-apertura").assertIsDisplayed()
        onNodeWithText(messaggio).assertIsDisplayed()
        catturaPng("shell-errore-apertura", width, height)
    }

    // H1: the error overlays the ConProgetto nav (still reachable), it does not replace it.
    private fun verificaErroreConProgetto(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val messaggio = "La cartella scelta non è valida."
        setContent {
            SchermataShell(
                stato = ShellUiStato.ConProgetto(
                    PROGETTO_PROVA,
                    OGNI_SEZIONE,
                    DestinazioneShell.REGISTRAZIONI,
                    erroreApertura = messaggio,
                ),
                azioni = AZIONI_VUOTE,
                contenuto = { Text("Contenuto della sezione selezionata") },
            )
        }
        onNodeWithText(PROGETTO_PROVA.nome).assertIsDisplayed()
        onNodeWithText("Registrazioni").assertIsDisplayed()
        onNodeWithText("Parlanti").assertIsDisplayed()
        onNodeWithTag("shell-errore-apertura").assertIsDisplayed()
        onNodeWithText(messaggio).assertIsDisplayed()
        catturaPng("shell-errore-con-progetto", width, height)
    }

    private fun verificaConProgetto(width: Int, height: Int, conParlanti: Boolean) =
        runDesktopComposeUiTest(width, height) {
            val sezioni = if (conParlanti) {
                setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)
            } else {
                setOf(DestinazioneShell.REGISTRAZIONI)
            }
            setContent {
                SchermataShell(
                    stato = ShellUiStato.ConProgetto(PROGETTO_PROVA, sezioni, DestinazioneShell.REGISTRAZIONI),
                    azioni = AZIONI_VUOTE,
                    contenuto = { Text("Contenuto della sezione selezionata") },
                )
            }
            onNodeWithText(PROGETTO_PROVA.nome).assertIsDisplayed()
            onNodeWithText("Registrazioni").assertIsDisplayed()
            onNodeWithText("Contenuto della sezione selezionata").assertIsDisplayed()
            if (conParlanti) {
                onNodeWithText("Parlanti").assertIsDisplayed()
            } else {
                onNodeWithText("Parlanti").assertDoesNotExist()
            }
            catturaPng("shell-con-progetto-parlanti-$conParlanti", width, height)
        }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int) {
        val png = File(outputDir, "$nome-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
