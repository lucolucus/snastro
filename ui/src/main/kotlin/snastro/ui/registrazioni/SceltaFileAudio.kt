package snastro.ui.registrazioni

/**
 * `tec-shell-ui` (owned here, in-process): S2's own audio-file picker, multi-select — "Importa file audio…" and
 * "Aggiungi parti…" ask [scegli] instead of building an OS dialog inside the composable (as
 * [snastro.ui.progetti.SceltaCartella] does for folders). `:avvio` implements it over `java.awt.FileDialog` owned by
 * the app window. Primitives only: the chosen files' absolute paths, in the order the dialog returns them; empty
 * when the user cancelled.
 */
fun interface SceltaFileAudio {
    fun scegli(): List<String>
}
