package snastro.avvio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import snastro.avvio.progetto.attendiScritturaRegistro
import snastro.avvio.smoke.eseguiSmoke
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * The app's ONE composition root (ADR 0030 §1): [costruisciGrafo] and [ContenutoApp] — S1, S2 with the Trascrizione
 * sources and the identification badge, S3 with the Voci panel, the Revisione UI and the Riassunto tab, S4 (the
 * shell's Parlanti section) and S5. `run` binding (profile): opens the real window. `--smoke <fixture-dir>` binding
 * (AC-237, AC-351, AC-357, AC-S151): [eseguiSmoke] — headless, on the ML Finte, exits 0.
 */
fun main(args: Array<String>) {
    val smokeIndex = args.indexOf("--smoke")
    if (smokeIndex >= 0) {
        val fixtureDir = args.getOrElse(smokeIndex + 1) { "build/smoke-fixture" }
        eseguiSmoke(fixtureDir)
        exitProcess(0)
    }

    // AC-C87..C90: the ONE rotating file handler, on the real per-user log folder — never under --smoke
    // (eseguiSmoke never calls this) nor in any :avvio test, so no test ever writes to it.
    configuraLoggingApp()

    // L464d (ADR 0010: v1 is Mac-only) — switches java.awt.FileDialog from picking FILES to picking
    // DIRECTORIES; must be set before any FileDialog is realized (SceltaCartellaFileDialog, below).
    System.setProperty("apple.awt.fileDialogForDirectories", "true")

    val grafo = costruisciGrafo()
    val icona = iconaApp()
    icona?.let(::iconaNelDock)
    val iconaFinestra = icona?.let { BitmapPainter(it.toComposeImageBitmap()) }
    application {
        var finestra by remember { mutableStateOf<ComposeWindow?>(null) }
        val esci = {
            // L627c: hide the window FIRST — the bounded join below (up to ATTESA_CHIUSURA_USCITA_MS)
            // then runs invisibly, never a frozen-looking window on close.
            finestra?.isVisible = false
            chiudiPrimaDiUscire(grafo.sessione::chiudi) // LOW-3: bounded, never a hung exit
            attendiScritturaRegistro() // L530e: bounded drain of the registry's own queued write
            exitApplication()
        }
        Window(onCloseRequest = esci, title = "snastro", icon = iconaFinestra) {
            finestra = window
            val sceltaCartella = remember { SceltaCartellaFileDialog(window) }
            ContenutoApp(grafo, sceltaCartella)
        }
    }
}

/** The app icon (`avvio/icone/snastro.svg`, 512 px), for the window; the packaged app uses `icone/snastro.icns`. */
private fun iconaApp(): BufferedImage? =
    Thread.currentThread().contextClassLoader.getResourceAsStream(RISORSA_ICONA)?.use(ImageIO::read)

/** Under `./gradlew :avvio:run` the Dock shows the JVM's icon unless told otherwise; the .app has its own. */
private fun iconaNelDock(icona: BufferedImage) {
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage = icona
    }
}

private const val RISORSA_ICONA = "icone/snastro.png"
