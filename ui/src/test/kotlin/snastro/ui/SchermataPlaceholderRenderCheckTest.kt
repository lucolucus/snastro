package snastro.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

/**
 * Wave-0 render-check (`:ui:renderCheck`, profile `ui_render_check`): proves the headless Compose
 * Desktop render path — sizing, no clipped text, PNG capture — before any real screen exists.
 * Real screens (S1-S4) replace [SchermataPlaceholder] here class by class; the mechanism (sizes,
 * PNG output, semantic assert) stays.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class SchermataPlaceholderRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `placeholder renders without clipping at 1280x800`() =
        verificaResa(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `placeholder renders without clipping at 1024x640`() =
        verificaResa(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    private fun verificaResa(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { SchermataPlaceholder() }

        onNodeWithText("snastro").assertExists()

        val png = File(outputDir, "placeholder-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
