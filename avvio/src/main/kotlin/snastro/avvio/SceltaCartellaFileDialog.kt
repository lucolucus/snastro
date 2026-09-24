package snastro.avvio

import snastro.ui.progetti.SceltaCartella
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * [SceltaCartella] over `java.awt.FileDialog` (frugality rung 3: the platform's own native picker),
 * OWNED by [finestra] — the app's real window (L464d: never a dialog with no owner, the former
 * `JFileChooser(null)` inside `SchermataProgetti`'s own composable). macOS (ADR 0010: v1 is Mac-only)
 * needs `apple.awt.fileDialogForDirectories=true`, set once in `main()` BEFORE any [FileDialog] is
 * realized (`Main.kt`), to switch it from picking files to picking folders — with that set,
 * [FileDialog.getFile] names the chosen folder and [FileDialog.getDirectory] its parent; either being
 * `null` means the user cancelled.
 *
 * Not covered by an automated test (like [ApriEsternoDesktop]): showing a real modal dialog and
 * reading back a human's pick cannot be driven headlessly — proven instead by the profile's
 * `ui_render_check`/manual run (the avvio smoke never exercises S1's pickers, AC-573's own two
 * buttons are outside its scripted path).
 */
internal class SceltaCartellaFileDialog(private val finestra: Frame) : SceltaCartella {
    override fun scegli(titolo: String): String? {
        val dialogo = FileDialog(finestra, titolo, FileDialog.LOAD)
        dialogo.isVisible = true
        val cartella = dialogo.directory
        val nome = dialogo.file
        return if (cartella != null && nome != null) File(cartella, nome).path else null
    }
}
