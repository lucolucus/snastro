package snastro.ui.registrazioni

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO

private const val LARGHEZZA_SONDA_PX = 320
private const val ALTEZZA_SONDA_PX = 64

/**
 * rilascio-ci (INV-I3 on other hosts): the host fingerprint of the S2 byte baseline. A fixed composable, independent
 * of S2 and of the app theme (text + an antialiased rounded shape), rendered through the SAME pipeline as the
 * fixtures (`runDesktopComposeUiTest` → `captureToImage` → ImageIO PNG): its SHA-256 changes exactly when the
 * host's rendering stack (fonts, antialiasing, Skia, JBR) does — the cause of the v1.5.0 CI failure — and not when
 * S2 changes. Call it outside any other compose test (it opens its own).
 */
@OptIn(ExperimentalTestApi::class)
internal fun improntaSondaDiResa(): String {
    var png = ByteArray(0)
    runDesktopComposeUiTest(LARGHEZZA_SONDA_PX, ALTEZZA_SONDA_PX) {
        setContent {
            MaterialTheme {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Sonda di resa · Àgg 0123 — Registrazioni", Modifier.padding(12.dp))
                }
            }
        }
        png = ByteArrayOutputStream().also { ImageIO.write(onRoot().captureToImage().toAwtImage(), "PNG", it) }
            .toByteArray()
    }
    return sha256(png)
}

internal fun sha256(byte: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(byte).joinToString("") { "%02x".format(it) }
