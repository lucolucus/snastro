package snastro.ui.lettore

import androidx.compose.ui.graphics.toArgb
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
import snastro.kernel.VoceId
import snastro.ui.SnastroTema
import snastro.ui.palette
import snastro.ui.stile.ColoriChiari
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

/** rework cycle 1, verifier AC-579: half the `Scrubber`'s own private `ALTEZZA_CORSIA` (8.dp), the y
 * offset from the Canvas' own top where a lane is actually painted. */
private const val Y_CORSIA_PX = 4

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [LettoreUiStato] fixture at both sizes —
 * sizing/overflow/contrast/state-rendering (AC-187, AC-189, AC-191). `BarraLettore` renders directly
 * from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class LettoreRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `inattivo mostra il pulsante riproduci a 1280x800`() = verificaInattivo(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `inattivo mostra il pulsante riproduci a 1024x640`() =
        verificaInattivo(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-191 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-191 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-189 sorgente non disponibile mostra un messaggio e il controllo disabilitato a 1280x800`() =
        verificaNonDisponibile(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-189 sorgente non disponibile mostra un messaggio e il controllo disabilitato a 1024x640`() =
        verificaNonDisponibile(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-187 in riproduzione mostra il pulsante pausa e la posizione a 1280x800`() =
        verificaProntoInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-187 in riproduzione mostra il pulsante pausa e la posizione a 1024x640`() =
        verificaProntoInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-187 in pausa mostra il pulsante riproduci e la posizione a 1280x800`() =
        verificaProntoInPausa(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-187 in pausa mostra il pulsante riproduci e la posizione a 1024x640`() =
        verificaProntoInPausa(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    private fun verificaInattivo(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { BarraLettore(LettoreUiStato.Inattivo, onRiproduci = {}, onPausa = {}) }
        onNodeWithTag("lettore-riproduci").assertIsDisplayed()
        catturaPng("lettore-inattivo", width, height)
    }

    private fun verificaCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { BarraLettore(LettoreUiStato.Caricamento, onRiproduci = {}, onPausa = {}) }
        onNodeWithTag("lettore-caricamento").assertIsDisplayed()
        catturaPng("lettore-caricamento", width, height)
    }

    private fun verificaNonDisponibile(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val messaggio = "Sorgente audio non disponibile."
        setContent {
            BarraLettore(LettoreUiStato.NonDisponibile(messaggio), onRiproduci = {}, onPausa = {})
        }
        onNodeWithTag("lettore-riproduci").assertIsDisplayed()
        onNodeWithTag("lettore-non-disponibile").assertIsDisplayed()
        onNodeWithText(messaggio).assertIsDisplayed()
        catturaPng("lettore-non-disponibile", width, height)
    }

    private fun verificaProntoInRiproduzione(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SnastroTema(scuro = scuro, riduciMovimento = true) {
                    BarraLettore(
                        stato = LettoreUiStato.Pronto(posizioneMs = 65_000, inRiproduzione = true),
                        onRiproduci = {},
                        onPausa = {},
                        durataMs = 185_000,
                        corsie = listOf(
                            snastro.ui.lettore.CorsiaVoce(snastro.kernel.VoceId(1), 0, 40_000),
                            snastro.ui.lettore.CorsiaVoce(snastro.kernel.VoceId(2), 45_000, 65_000),
                        ),
                    )
                }
            }
            onNodeWithTag("lettore-pausa").assertIsDisplayed()
            onNodeWithText("1:05").assertIsDisplayed() // AC-557: no leading zero on minutes under one hour
            val suffisso = if (scuro) "-scuro" else ""
            catturaPng("lettore-pronto-in-riproduzione$suffisso", width, height)
        }

    @Test
    fun `AC-579 in riproduzione con corsie delle voci scuro a 1280x800`() =
        verificaProntoInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-579 in riproduzione con corsie delle voci scuro a 1024x640`() =
        verificaProntoInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    private fun verificaProntoInPausa(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            BarraLettore(
                stato = LettoreUiStato.Pronto(posizioneMs = 65_000, inRiproduzione = false),
                onRiproduci = {},
                onPausa = {},
            )
        }
        onNodeWithTag("lettore-riproduci").assertIsDisplayed()
        onNodeWithText("1:05").assertIsDisplayed() // AC-557: no leading zero on minutes under one hour
        catturaPng("lettore-pronto-in-pausa", width, height)
    }

    /** rework cycle 1, verifier AC-579: the `Scrubber`'s lanes are Canvas-painted pixels, not semantics
     * nodes — sampled at a fraction of the `lettore-scrubber` node's own width, at the lane's own y. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.coloreCorsia(xFrazione: Float): Int {
        val bounds = onNodeWithTag("lettore-scrubber").fetchSemanticsNode().boundsInRoot
        val x = (bounds.left + xFrazione * bounds.width).toInt()
        val y = (bounds.top + Y_CORSIA_PX).toInt()
        return onRoot().captureToImage().toAwtImage().getRGB(x, y)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `AC-579 le corsie sono dipinte proporzionalmente alla durata, un colore per Voce`() =
        runDesktopComposeUiTest(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX) {
            setContent {
                BarraLettore(
                    stato = LettoreUiStato.Pronto(posizioneMs = 0, inRiproduzione = false),
                    onRiproduci = {},
                    onPausa = {},
                    durataMs = 4_000,
                    corsie = listOf(CorsiaVoce(VoceId(1), 0, 2_000), CorsiaVoce(VoceId(2), 2_000, 4_000)),
                )
            }
            assertEquals(palette(VoceId(1), ColoriChiari).toArgb(), coloreCorsia(0.25f))
            assertEquals(palette(VoceId(2), ColoriChiari).toArgb(), coloreCorsia(0.75f))
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `AC-579 senza Segmenti nessuna corsia, la traccia resta comunque visibile`() =
        runDesktopComposeUiTest(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX) {
            setContent {
                BarraLettore(
                    stato = LettoreUiStato.Pronto(posizioneMs = 0, inRiproduzione = false),
                    onRiproduci = {},
                    onPausa = {},
                    durataMs = 4_000,
                    corsie = emptyList(),
                )
            }
            val corsia = coloreCorsia(0.5f)
            assertNotEquals(palette(VoceId(1), ColoriChiari).toArgb(), corsia)
            assertNotEquals(palette(VoceId(2), ColoriChiari).toArgb(), corsia)
            onNodeWithTag("lettore-scrubber").assertIsDisplayed()
        }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int) {
        val png = File(outputDir, "$nome-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
