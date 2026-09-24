package snastro.ui.modelli

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
import snastro.ui.formattaByte
import snastro.ui.testi.ETICHETTA_LICENZE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SCARICA
import snastro.ui.testi.etichettaModelliMancanti
import snastro.ui.testi.messaggioPer
import java.io.File
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val AZIONI_VUOTE = AzioniModelli(scarica = {})

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [ModelliUiStato] fixture at both sizes, both
 * themes — sizing/overflow/contrast/state-rendering (AC-227..232/578). Six states: not downloaded
 * (AC-227), downloading with progress (AC-228), ready (AC-231/232), error — hash mismatch and error —
 * no network (AC-229/230), and partial (a Mancanti with fewer entries/bytes left than a fresh
 * install, distinguishing "nothing downloaded yet" from "some already installed"). [SchermataModelli]
 * renders directly from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class ModelliRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-227 mancanti mostra la dimensione totale e Scarica a 1280x800`() =
        verificaMancanti(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-227 mancanti mostra la dimensione totale e Scarica a 1024x640`() =
        verificaMancanti(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-227 mancanti mostra la dimensione totale e Scarica a 1280x800 (scuro)`() =
        verificaMancanti(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-227 mancanti mostra la dimensione totale e Scarica a 1024x640 (scuro)`() =
        verificaMancanti(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-227 download parziale mostra il residuo e Scarica a 1280x800`() =
        verificaParziale(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-227 download parziale mostra il residuo e Scarica a 1024x640`() =
        verificaParziale(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-227 download parziale mostra il residuo e Scarica a 1280x800 (scuro)`() =
        verificaParziale(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-227 download parziale mostra il residuo e Scarica a 1024x640 (scuro)`() =
        verificaParziale(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-228 download in corso mostra l avanzamento per modello a 1280x800`() =
        verificaInDownload(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-228 download in corso mostra l avanzamento per modello a 1024x640`() =
        verificaInDownload(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-228 download in corso mostra l avanzamento per modello a 1280x800 (scuro)`() =
        verificaInDownload(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-228 download in corso mostra l avanzamento per modello a 1024x640 (scuro)`() =
        verificaInDownload(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-229 hash non valido mostra l errore e Riprova a 1280x800`() =
        verificaErroreHash(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-229 hash non valido mostra l errore e Riprova a 1024x640`() =
        verificaErroreHash(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-229 hash non valido mostra l errore e Riprova a 1280x800 (scuro)`() =
        verificaErroreHash(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-229 hash non valido mostra l errore e Riprova a 1024x640 (scuro)`() =
        verificaErroreHash(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-230 rete assente mostra il messaggio e Riprova a 1280x800`() =
        verificaErroreRete(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-230 rete assente mostra il messaggio e Riprova a 1024x640`() =
        verificaErroreRete(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-230 rete assente mostra il messaggio e Riprova a 1280x800 (scuro)`() =
        verificaErroreRete(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-230 rete assente mostra il messaggio e Riprova a 1024x640 (scuro)`() =
        verificaErroreRete(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-231 AC-232 pronti mostra le licenze a 1280x800`() =
        verificaPronti(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-231 AC-232 pronti mostra le licenze a 1024x640`() =
        verificaPronti(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-231 AC-232 pronti mostra le licenze a 1280x800 (scuro)`() =
        verificaPronti(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-231 AC-232 pronti mostra le licenze a 1024x640 (scuro)`() =
        verificaPronti(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    private fun verificaMancanti(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataModelli(
                    ModelliUiStato.Mancanti(numero = 4, totaleByte = 521_000_000),
                    AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(etichettaModelliMancanti(4)).assertIsDisplayed()
            onNodeWithText(formattaByte(521_000_000)).assertIsDisplayed()
            onNodeWithTag("modelli-scarica").assertIsDisplayed()
            onNodeWithText(ETICHETTA_SCARICA).assertIsDisplayed()
            catturaPng("modelli-mancanti", width, height, scuro)
        }

    // "partial": one entry already installed (cache from a previous run/crash) — fewer left, smaller
    // total, but still Mancanti with the same 'Scarica' affordance, never confused with a fresh install.
    private fun verificaParziale(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataModelli(
                    ModelliUiStato.Mancanti(numero = 1, totaleByte = 26_530_550),
                    AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(etichettaModelliMancanti(1)).assertIsDisplayed()
            onNodeWithText(formattaByte(26_530_550)).assertIsDisplayed()
            onNodeWithTag("modelli-scarica").assertIsDisplayed()
            catturaPng("modelli-parziale", width, height, scuro)
        }

    private fun verificaInDownload(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataModelli(
                    ModelliUiStato.InDownload(
                        modelloId = "asr-parakeet-tdt-0.6b-v3-int8",
                        scaricatiByte = 200_000_000,
                        totaliByte = 487_170_055,
                    ),
                    AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("modelli-download").assertIsDisplayed()
            onNodeWithTag("modelli-progresso").assertIsDisplayed()
            onNodeWithText("asr-parakeet-tdt-0.6b-v3-int8", substring = true).assertIsDisplayed()
            catturaPng("modelli-in-download", width, height, scuro)
        }

    private fun verificaErroreHash(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val messaggio = messaggioPer(ErroreServizioModelli.HashNonValido("asr-parakeet-tdt-0.6b-v3-int8"))
            setContent {
                SchermataModelli(ModelliUiStato.Errore(messaggio), AZIONI_VUOTE, scuro = scuro, riduciMovimento = true)
            }
            onNodeWithTag("modelli-errore").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
            onNodeWithTag("modelli-riprova").assertIsDisplayed()
            onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
            catturaPng("modelli-errore-hash", width, height, scuro)
        }

    private fun verificaErroreRete(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val messaggio = messaggioPer(ErroreServizioModelli.ReteAssente)
            setContent {
                SchermataModelli(ModelliUiStato.Errore(messaggio), AZIONI_VUOTE, scuro = scuro, riduciMovimento = true)
            }
            onNodeWithTag("modelli-errore").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
            onNodeWithTag("modelli-riprova").assertIsDisplayed()
            catturaPng("modelli-errore-rete", width, height, scuro)
        }

    private fun verificaPronti(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val licenze = listOf(
                unaLicenzaVista(nome = "Parakeet TDT 0.6B v3", ruolo = "riconoscimento"),
                unaLicenzaVista(
                    nome = "Segmentazione pyannote 3.0",
                    ruolo = "segmentazione",
                    licenza = "MIT",
                    attribuzione = "pyannote segmentation-3.0 (MIT), © pyannote (Hervé Bredin); " +
                        "ONNX export by k2-fsa sherpa-onnx",
                ),
                unaLicenzaVista(
                    nome = "WeSpeaker ResNet34-LM",
                    ruolo = "embedding (diarization)",
                    attribuzione = "WeSpeaker ResNet34-LM (CC-BY-4.0), WeSpeaker team; trained on VoxCeleb",
                ),
                unaLicenzaVista(
                    nome = "Silero VAD",
                    ruolo = "vad",
                    licenza = "MIT",
                    attribuzione = "Silero VAD (MIT), snakers4/silero-vad",
                ),
            )
            setContent {
                SchermataModelli(ModelliUiStato.Pronti(licenze), AZIONI_VUOTE, scuro = scuro, riduciMovimento = true)
            }
            onNodeWithText(ETICHETTA_LICENZE).assertIsDisplayed()
            onNodeWithTag("modelli-licenze").assertIsDisplayed()
            onNodeWithText("Parakeet TDT 0.6B v3 · riconoscimento").assertIsDisplayed()
            catturaPng("modelli-pronti", width, height, scuro)
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
