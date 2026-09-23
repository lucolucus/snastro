package snastro.audio

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock

/**
 * Probes a source audio file with FFmpeg, before it is decoded (ADR 0005): does it open as media,
 * does it have an audio stream, how long is it, when was it recorded. Read-only — never writes [file].
 * [clock] gives "now" and the time zone of the recording date (AC-364).
 */
public class SondaFfmpeg(private val clock: Clock = Clock.systemDefaultZone()) {
    /**
     * @return the duration (always > 0 for a probed source) and the recording date chosen by
     *   [dataRegistrazione] (AC-364): the `creation_time` metadata, else the file's birth time, else
     *   its last-modified time.
     * @throws AudioIlleggibile [file] is missing, empty, a directory, or unreadable as media.
     * @throws FormatoNonSupportato [file] opens fine but has no audio stream.
     */
    public fun sonda(file: Path): InfoFile {
        val grabber = apriGrabberAudio(file)
        try {
            val durataMs = grabber.lengthInTime / MICROSECONDI_PER_MS
            val attributi = Files.readAttributes(file, BasicFileAttributes::class.java)
            val data = dataRegistrazione(
                creationTime = grabber.metadata?.get(CHIAVE_CREATION_TIME),
                creazioneFile = attributi.creationTime()?.toInstant(),
                modificaFile = attributi.lastModifiedTime().toInstant(),
                adesso = clock.instant(),
                zona = clock.zone,
            )
            return InfoFile(durataMs, data)
        } finally {
            chiudi(grabber)
        }
    }

    private companion object {
        const val MICROSECONDI_PER_MS = 1_000L
        const val CHIAVE_CREATION_TIME = "creation_time"
    }
}
