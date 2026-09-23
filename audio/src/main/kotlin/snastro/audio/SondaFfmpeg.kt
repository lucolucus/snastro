package snastro.audio

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId

/**
 * Probes a source audio file with FFmpeg, before it is decoded (ADR 0005): does it open as media,
 * does it have an audio stream, how long is it. Read-only — never writes [file].
 */
public class SondaFfmpeg {
    /**
     * @return the duration (always > 0 for a probed source) and [file]'s own last-modified date.
     * @throws AudioIlleggibile [file] is missing, empty, a directory, or unreadable as media.
     * @throws FormatoNonSupportato [file] opens fine but has no audio stream.
     */
    public fun sonda(file: Path): InfoFile {
        val grabber = apriGrabberAudio(file)
        try {
            val durataMs = grabber.lengthInTime / MICROSECONDI_PER_MS
            val modificatoIl = Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            return InfoFile(durataMs, modificatoIl)
        } finally {
            chiudi(grabber)
        }
    }

    private companion object {
        const val MICROSECONDI_PER_MS = 1_000L
    }
}
