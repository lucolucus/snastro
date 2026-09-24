package snastro.ui.progetti

/**
 * `tec-shell-ui` (owned here, in-process, `seam-in-process`): S1's own directory picker — the "Cambia
 * cartella…"/"Apri progetto…" buttons of [snastro.ui.progetti.SchermataProgetti] ask [scegli] instead
 * of instantiating an OS dialog themselves (L464d: a `JFileChooser` built with a `null` owner is no
 * longer a composable's own concern). `:avvio` implements this over `java.awt.FileDialog`, OWNED by
 * the app window (constructed once, in the composition root). Primitives only (Published Language):
 * [titolo] the dialog's own title; the result is the chosen folder's absolute path, or `null` if the
 * user cancelled.
 */
fun interface SceltaCartella {
    fun scegli(titolo: String): String?
}
