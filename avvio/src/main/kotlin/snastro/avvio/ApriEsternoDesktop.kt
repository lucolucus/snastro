package snastro.avvio

import snastro.ui.ApriEsterno
import java.awt.Desktop
import java.io.File

/**
 * [ApriEsterno] over `java.awt.Desktop` (frugality rung 3: the platform's own file-manager/default-
 * app integration over a hand-rolled per-OS launcher). Not reachable from any R0 screen (AC-350: no
 * S3/S4/S5) — wired here so `avvio-composizione`/`avvio-parlanti` (R1/R2) only need to plug their
 * screens in, never re-build this adapter.
 */
internal class ApriEsternoDesktop : ApriEsterno {
    override fun apriFile(percorso: String) {
        Desktop.getDesktop().open(File(percorso))
    }

    override fun mostraNellaCartella(percorso: String) {
        Desktop.getDesktop().browseFileDirectory(File(percorso))
    }
}
