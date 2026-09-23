package snastro.modelli

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/** Shared constants and helpers of the install protocol (ADR 0008 Amendment (c)). */
internal object ProtocolloInstallazione {
    /** Marker file inside `<id>/`, holding the installed asset's SHA-256 (lowercase hex). */
    const val MARCATORE = ".sha256"

    /** Recursively deletes [radice]; a no-op if it does not exist. */
    fun eliminaRicorsivo(radice: Path) {
        if (!Files.exists(radice)) return
        Files.walk(radice).use { flusso ->
            flusso.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }
}
