package snastro.avvio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private const val LARGHEZZA_SMOKE_PX = 1280
private const val ALTEZZA_SMOKE_PX = 800

/**
 * Composition root (wave-0 scaffold). `run` binding (profile): opens an empty window. `--smoke
 * <dir>` binding: renders the same empty content offscreen, writes one PNG, exits 0 — the run/smoke
 * proof the gate needs before any real screen exists (`:ui` blocks replace [ContenutoVuoto] with the
 * real screens; the mechanism here stays).
 */
fun main(args: Array<String>) {
    val smokeIndex = args.indexOf("--smoke")
    if (smokeIndex >= 0) {
        val fixtureDir = args.getOrElse(smokeIndex + 1) { "build/smoke-fixture" }
        eseguiSmoke(fixtureDir)
        exitProcess(0)
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "snastro") {
            ContenutoVuoto()
        }
    }
}

@Composable
private fun ContenutoVuoto() {
    MaterialTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun eseguiSmoke(fixtureDir: String) {
    // Wave-0 scaffold: no domain yet, so the fixture project dir is accepted but not opened.
    File(fixtureDir).mkdirs()
    val outputDir = File("build/smoke").apply { mkdirs() }

    runDesktopComposeUiTest(LARGHEZZA_SMOKE_PX, ALTEZZA_SMOKE_PX) {
        setContent { ContenutoVuoto() }
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", File(outputDir, "smoke.png"))
    }
}
