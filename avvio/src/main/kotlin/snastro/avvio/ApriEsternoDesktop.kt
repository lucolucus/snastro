package snastro.avvio

import snastro.ui.ApriEsterno
import java.awt.Desktop
import java.io.File

/**
 * [ApriEsterno] over `java.awt.Desktop` (frugality rung 3: the platform's own file-manager/default-
 * app integration over a hand-rolled per-OS launcher): S3's "Apri sbobinatura" / "Mostra nella cartella", built ONCE
 * by `costruisciGrafo` for the whole app.
 */
internal class ApriEsternoDesktop : ApriEsterno {
    override fun apriFile(percorso: String) {
        Desktop.getDesktop().open(File(percorso))
    }

    override fun mostraNellaCartella(percorso: String) {
        Desktop.getDesktop().browseFileDirectory(File(percorso))
    }
}
