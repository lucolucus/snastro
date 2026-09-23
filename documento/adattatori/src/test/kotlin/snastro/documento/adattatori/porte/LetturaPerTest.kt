package snastro.documento.adattatori.porte

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Reads [file]'s full content as UTF-8 text — for test assertions ONLY, via [FileChannel] rather
 * than any of the convenience read APIs ADR 0010's `enforced_by` bans across this whole
 * `documento` module. That ban targets [ScrittoreDocumentoFile] (and any future `documento`
 * consumer reading a `.md` back, [INV-23]), not a test observing what actually landed on disk.
 */
internal fun leggiPerTest(file: Path): String {
    val dimensione = Files.size(file)
    val buffer = ByteBuffer.allocate(dimensione.toInt())
    FileChannel.open(file, StandardOpenOption.READ).use { canale ->
        while (buffer.hasRemaining()) {
            if (canale.read(buffer) < 0) break
        }
    }
    buffer.flip()
    return Charsets.UTF_8.decode(buffer).toString()
}

/** Names of every regular file currently in [cartella] (no content read — just directory entries). */
internal fun elencoNomiFile(cartella: Path): Set<String> =
    Files.newDirectoryStream(cartella).use { flusso ->
        flusso.filter { Files.isRegularFile(it) }.map { it.fileName.toString() }.toSet()
    }
