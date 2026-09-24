package snastro.audio

import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Decodes a source audio file to a derived 16 kHz mono 16-bit PCM WAV (once, ADR 0005), and reads
 * samples back from it. [leggiCampioni] never touches FFmpeg: it reads the WAV [decodificaInWav]
 * itself wrote ([FormatoWav]), so it costs only the bytes of the requested slice, not the whole file.
 */
public class DecodificaFfmpeg {
    /**
     * Decodes [sorgente] and resamples it to 16 kHz mono, writing a 16-bit PCM WAV to [destinazione]
     * (creating parent directories as needed). Streams frame by frame — never holds the whole
     * recording in memory. L502d/L559b: writes to a sibling `.tmp` file first, then
     * [Files.move]s it into place with `ATOMIC_MOVE` — a crash or I/O failure at any point before that
     * rename leaves [destinazione]'s previous content (or its absence) untouched, never a half-written
     * derived WAV mistaken for a good one (the temp file itself is always swept, success or failure).
     *
     * @throws AudioIlleggibile [sorgente] is missing, empty, a directory, or unreadable as media.
     * @throws FormatoNonSupportato [sorgente] opens fine but has no audio stream.
     */
    public fun decodificaInWav(sorgente: Path, destinazione: Path) {
        val grabber = apriGrabberAudio(sorgente)
        try {
            grabber.audioChannels = 1
            grabber.sampleRate = FREQUENZA_CAMPIONAMENTO_HZ
            destinazione.parent?.let(Files::createDirectories)
            val temporaneo = destinazione.resolveSibling("${destinazione.fileName}.$SUFFISSO_TEMPORANEO")
            try {
                RandomAccessFile(temporaneo.toFile(), "rw").use { raf -> scriviWav(raf, grabber, sorgente) }
                Files.move(
                    temporaneo,
                    destinazione,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } finally {
                Files.deleteIfExists(temporaneo) // no-op una volta che move l'ha gia' rinominato via
            }
        } finally {
            chiudi(grabber)
        }
    }

    /**
     * The samples of `[inizioMs, fineMs)` from [wav] (a WAV [decodificaInWav] produced): exactly
     * `(fineMs - inizioMs) * 16` of them, normalized to `[-1, 1]`. Zero-padded (silence) past the end
     * of [wav] — an interval starting at or after the end is all zeros. Never throws for that case.
     */
    public fun leggiCampioni(wav: Path, inizioMs: Long, fineMs: Long): FloatArray {
        require(inizioMs >= 0) { "inizioMs negativo: $inizioMs" }
        require(inizioMs < fineMs) { "inizioMs ($inizioMs) deve precedere fineMs ($fineMs)" }

        val richiesti = ((fineMs - inizioMs) * CAMPIONI_PER_MS).toInt()
        val risultato = FloatArray(richiesti)
        RandomAccessFile(wav.toFile(), "r").use { raf -> leggiIn(raf, byteDelCampione(inizioMs), risultato) }
        return risultato
    }

    /** Fills [risultato] from [partenza] on, leaving the tail zero (silence) past the end of [raf]. */
    private fun leggiIn(raf: RandomAccessFile, partenza: Long, risultato: FloatArray) {
        val lunghezzaFile = raf.length()
        if (partenza >= lunghezzaFile) return

        raf.seek(partenza)
        val disponibili = minOf((risultato.size * BYTE_PER_CAMPIONE).toLong(), lunghezzaFile - partenza).toInt()
        val letti = ByteArray(disponibili)
        raf.readFully(letti)
        val bb = ByteBuffer.wrap(letti).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (bb.remaining() >= BYTE_PER_CAMPIONE) {
            risultato[i] = bb.short / AMPIEZZA_MASSIMA
            i++
        }
    }

    private fun scriviWav(raf: RandomAccessFile, grabber: FFmpegFrameGrabber, sorgente: Path) {
        raf.setLength(0)
        raf.seek(INTESTAZIONE_WAV_BYTE)
        var byteScritti = 0L
        while (true) {
            val canale = prossimoCanale(grabber, sorgente) ?: break
            byteScritti += scriviCampioni(raf, canale)
        }
        raf.seek(0)
        raf.write(intestazioneWav(byteScritti))
    }

    /** The mono channel of the next audio frame, or null at the end of [sorgente]. */
    private fun prossimoCanale(grabber: FFmpegFrameGrabber, sorgente: Path): ShortBuffer? {
        val frame = try {
            grabber.grabSamples()
        } catch (e: org.bytedeco.javacv.FrameGrabber.Exception) {
            throw AudioIlleggibile(sorgente, e)
        }
        return frame?.samples?.firstOrNull() as? ShortBuffer
    }

    private fun scriviCampioni(out: RandomAccessFile, campioni: ShortBuffer): Int {
        val n = campioni.remaining()
        val buffer = ByteBuffer.allocate(n * BYTE_PER_CAMPIONE).order(ByteOrder.LITTLE_ENDIAN)
        val lettura = campioni.duplicate()
        while (lettura.hasRemaining()) buffer.putShort(lettura.get())
        out.write(buffer.array())
        return n * BYTE_PER_CAMPIONE
    }

    private companion object {
        const val AMPIEZZA_MASSIMA = 32_768f
        const val SUFFISSO_TEMPORANEO = "tmp"
    }
}
