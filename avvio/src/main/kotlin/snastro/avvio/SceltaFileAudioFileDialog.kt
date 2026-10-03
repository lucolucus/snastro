package snastro.avvio

import snastro.ui.registrazioni.SceltaFileAudio
import java.awt.FileDialog
import java.awt.Frame

/** macOS's JVM-global switch of [FileDialog] between picking files (`false`) and folders (`true`). */
internal const val PROPRIETA_CARTELLE = "apple.awt.fileDialogForDirectories"

/**
 * [SceltaFileAudio] over `java.awt.FileDialog` (frugality rung 3: the macOS native picker), OWNED by [finestra] —
 * the app window — with `isMultipleMode` on. [PROPRIETA_CARTELLE] is set to `false` right before the dialog is
 * created, since [SceltaCartellaFileDialog] sets it to `true` for its own. No `FilenameFilter` (the macOS
 * dialog may ignore it): the import itself rejects non-audio files.
 *
 * Not covered by an automated test (like [SceltaCartellaFileDialog]): showing a real modal dialog and reading back
 * a human's pick cannot be driven headlessly; the screen's behaviour on 0/1/N paths is tested in `:ui` with a fake.
 */
internal class SceltaFileAudioFileDialog(private val finestra: Frame) : SceltaFileAudio {
    override fun scegli(): List<String> {
        System.setProperty(PROPRIETA_CARTELLE, "false")
        val dialogo = FileDialog(finestra, "Scegli file audio", FileDialog.LOAD).apply { isMultipleMode = true }
        dialogo.isVisible = true
        return dialogo.files.map { it.absolutePath }
    }
}
