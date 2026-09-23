package snastro.modelli

import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

/** The `<cartella>/<id>.part` file [ScaricamentoAsset] streams a [VoceCatalogo]'s asset into. */
internal class FileParziale(private val percorso: Path) {
    fun dimensione(): Long = if (Files.isRegularFile(percorso)) Files.size(percorso) else 0L

    fun elimina() {
        Files.deleteIfExists(percorso)
    }

    /** Throws on failure — the caller maps the `IOException` to `ScritturaFallita`. */
    fun apriScrittura(riprende: Boolean): OutputStream = if (riprende) {
        Files.newOutputStream(percorso, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    } else {
        Files.newOutputStream(
            percorso,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    fun sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(percorso).use { flusso ->
            val buffer = ByteArray(DIMENSIONE_BUFFER)
            while (true) {
                val letti = flusso.read(buffer)
                if (letti == -1) break
                digest.update(buffer, 0, letti)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private companion object {
        const val DIMENSIONE_BUFFER = 64 * 1024
    }
}
