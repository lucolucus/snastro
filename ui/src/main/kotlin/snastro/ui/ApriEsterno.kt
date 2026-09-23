package snastro.ui

/**
 * `tec-shell-ui` (owned here): opens something outside the app — the OS default app for a file, or a
 * file manager window on its containing folder (S3 "Apri documento" / "Mostra nella cartella", R8).
 */
interface ApriEsterno {
    fun apriFile(percorso: String)

    fun mostraNellaCartella(percorso: String)
}
