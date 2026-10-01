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
     * @return the duration (always > 0 for a probed source), the recording date and the start time chosen by
     *   [dataRegistrazione] (ADR 0040): `moov/udta/date` gives both; without it the time is empty and the date
     *   is (AC-364) the `creation_time` metadata, else the file's birth time, else its last-modified time.
     * @throws AudioIlleggibile [file] is missing, empty, a directory, or unreadable as media.
     * @throws FormatoNonSupportato [file] opens fine but has no audio stream.
     */
    public fun sonda(file: Path): InfoFile {
        val grabber = apriGrabberAudio(file)
        try {
            val durataMs = grabber.lengthInTime / MICROSECONDI_PER_MS
            val attributi = Files.readAttributes(file, BasicFileAttributes::class.java)
            val inizio = dataRegistrazione(
                udtaDate = leggiUdtaDate(file),
                creationTime = grabber.metadata?.get(CHIAVE_CREATION_TIME),
                creazioneFile = attributi.creationTime()?.toInstant(),
                modificaFile = attributi.lastModifiedTime().toInstant(),
                clock = clock,
            )
            return InfoFile(durataMs, inizio.data, inizio.ora)
        } finally {
            chiudi(grabber)
        }
    }

    private companion object {
        const val MICROSECONDI_PER_MS = 1_000L
        const val CHIAVE_CREATION_TIME = "creation_time"
    }
}
