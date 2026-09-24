package snastro.ui.progetti

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.ErroreSessione
import snastro.ui.formattaData
import snastro.ui.testi.ETICHETTA_APRI_PROGETTO
import snastro.ui.testi.ETICHETTA_NUOVO_PROGETTO
import snastro.ui.testi.MESSAGGIO_PROGETTI_VUOTO
import snastro.ui.testi.etichettaRegistrazioni
import snastro.ui.testi.messaggioPer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val AZIONI_VUOTE = AzioniProgetti(crea = { _, _ -> }, apri = {}, chiudiErroreCrea = {}, chiudiErroreApri = {})
private const val CARTELLA_GENITORE_DI_PROVA = "/tmp/snastro"
private val UN_PROGETTO = ProgettoVista(
    progettoId = ProgettoId("id-1"),
    nome = "Consiglio comunale",
    percorso = "/progetti/Consiglio comunale.snastro",
    numRegistrazioni = 3,
    ultimaAttivita = Instant.parse("2026-09-23T10:15:30Z"),
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [ProgettiUiStato] fixture at both sizes, both
 * themes — sizing/overflow/contrast/state-rendering (AC-192/193/194/195/196/197/198/573).
 * [SchermataProgetti] renders directly from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class ProgettiRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-193 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-193 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-193 caricamento mostra un indicatore a 1280x800 (scuro)`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-193 caricamento mostra un indicatore a 1024x640 (scuro)`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-192 lista vuota mostra il messaggio dedicato a 1280x800`() =
        verificaListaVuota(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-192 lista vuota mostra il messaggio dedicato a 1024x640`() =
        verificaListaVuota(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-192 lista vuota mostra il messaggio dedicato a 1280x800 (scuro)`() =
        verificaListaVuota(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-192 lista vuota mostra il messaggio dedicato a 1024x640 (scuro)`() =
        verificaListaVuota(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-198 la lista mostra nome numero di registrazioni e ultima attivita a 1280x800`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-198 la lista mostra nome numero di registrazioni e ultima attivita a 1024x640`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-198 la lista mostra nome numero di registrazioni e ultima attivita a 1280x800 (scuro)`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-198 la lista mostra nome numero di registrazioni e ultima attivita a 1024x640 (scuro)`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `creazione in corso disabilita i controlli e mostra l indicatore a 1280x800`() =
        verificaInCorso(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `creazione in corso disabilita i controlli e mostra l indicatore a 1024x640`() =
        verificaInCorso(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `creazione in corso disabilita i controlli e mostra l indicatore a 1280x800 (scuro)`() =
        verificaInCorso(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `creazione in corso disabilita i controlli e mostra l indicatore a 1024x640 (scuro)`() =
        verificaInCorso(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-195 un errore di creazione e mostrato inline a 1280x800`() =
        verificaErroreCrea(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-195 un errore di creazione e mostrato inline a 1024x640`() =
        verificaErroreCrea(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-195 un errore di creazione e mostrato inline a 1280x800 (scuro)`() =
        verificaErroreCrea(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-195 un errore di creazione e mostrato inline a 1024x640 (scuro)`() =
        verificaErroreCrea(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-196 197 un errore di apertura e mostrato inline a 1280x800`() =
        verificaErroreApri(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-196 197 un errore di apertura e mostrato inline a 1024x640`() =
        verificaErroreApri(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-196 197 un errore di apertura e mostrato inline a 1280x800 (scuro)`() =
        verificaErroreApri(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-196 197 un errore di apertura e mostrato inline a 1024x640 (scuro)`() =
        verificaErroreApri(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    private fun verificaCaricamento(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Caricamento,
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("progetti-indicatore-caricamento").assertIsDisplayed()
            catturaPng("progetti-caricamento", width, height, scuro)
        }

    private fun verificaListaVuota(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList()),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(MESSAGGIO_PROGETTI_VUOTO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_NUOVO_PROGETTO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_APRI_PROGETTO).assertIsDisplayed()
            catturaPng("progetti-vuoto", width, height, scuro)
        }

    private fun verificaLista(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = listOf(UN_PROGETTO)),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(UN_PROGETTO.nome).assertIsDisplayed()
            val dataUltimaAttivita =
                formattaData(UN_PROGETTO.ultimaAttivita.atZone(ZoneId.systemDefault()).toLocalDate())
            onNodeWithText("${etichettaRegistrazioni(UN_PROGETTO.numRegistrazioni)} · $dataUltimaAttivita")
                .assertIsDisplayed()
            catturaPng("progetti-lista", width, height, scuro)
        }

    private fun verificaInCorso(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList(), inCorso = true),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            // `inCorso` is one shared flag (M3: guards ANY new crea/apri while one is in flight) — both
            // controls are disabled together, not just the one that started the operation.
            onNodeWithTag("progetti-crea").assertIsNotEnabled()
            onNodeWithTag("progetti-apri").assertIsNotEnabled()
            onNodeWithTag("progetti-operazione-in-corso").assertIsDisplayed()
            catturaPng("progetti-in-corso", width, height, scuro)
        }

    private fun verificaErroreCrea(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val messaggio = messaggioPer(ErroreSessione.CartellaNonValida)
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = emptyList(), erroreCrea = messaggio),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("progetti-errore-crea").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
            catturaPng("progetti-errore-crea", width, height, scuro)
        }

    private fun verificaErroreApri(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val messaggio = messaggioPer(ErroreSessione.ProgettoGiaAperto)
            setContent {
                SchermataProgetti(
                    stato = ProgettiUiStato.Dati(progetti = listOf(UN_PROGETTO), erroreApri = messaggio),
                    azioni = AZIONI_VUOTE,
                    cartellaGenitorePredefinita = CARTELLA_GENITORE_DI_PROVA,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("progetti-errore-apri").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
            catturaPng("progetti-errore-apri", width, height, scuro)
        }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int, scuro: Boolean = false) {
        val suffisso = if (scuro) "-scuro" else ""
        val png = File(outputDir, "$nome$suffisso-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
