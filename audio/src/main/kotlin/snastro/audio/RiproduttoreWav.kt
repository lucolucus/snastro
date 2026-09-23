package snastro.audio

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays a WAV [DecodificaFfmpeg] produced, from an exact sample offset (ADR 0005). [intervalli], when
 * given, are played in sequence and playback stops after the last one (an `EstrattoRef`/`EstrattoAudio`);
 * `null` means the single virtual interval `[0, durata del wav)`. [daMs] is an offset into that
 * CONCATENATED timeline — not into the WAV file — so resuming with the same [intervalli] and
 * `daMs = `[posizioneMs] continues where a previous [pausa] left off. One playback at a time: a new
 * [riproduci] call replaces whatever is currently playing.
 */
public class RiproduttoreWav : AutoCloseable {
    @Volatile
    private var thread: Thread? = null

    @Volatile
    private var interrompi = false

    @Volatile
    private var posizione: Long = 0

    @Volatile
    private var attivo = false

    public fun riproduci(wav: Path, intervalli: List<Pair<Long, Long>>?, daMs: Long) {
        pausa()
        val sequenza = intervalli ?: listOf(0L to durataMs(wav))
        posizione = daMs
        interrompi = false
        attivo = true
        thread = Thread({
            try {
                riproduciSequenza(wav, sequenza, daMs)
            } finally {
                attivo = false
            }
        }, "riproduttore-wav").apply {
            isDaemon = true
            start()
        }
    }

    public fun pausa() {
        interrompi = true
        thread?.join(ATTESA_ARRESTO_MS)
        thread = null
        attivo = false
    }

    public fun posizioneMs(): Long = posizione

    public fun inRiproduzione(): Boolean = attivo

    override fun close() {
        pausa()
    }

    private fun durataMs(wav: Path): Long {
        val byteCampioni = Files.size(wav) - INTESTAZIONE_WAV_BYTE
        return byteCampioni / (CAMPIONI_PER_MS * BYTE_PER_CAMPIONE)
    }

    private fun riproduciSequenza(wav: Path, sequenza: List<Pair<Long, Long>>, daMs: Long) {
        val formato = AudioFormat(FREQUENZA_CAMPIONAMENTO_HZ.toFloat(), BIT_PER_CAMPIONE, 1, true, false)
        val line = AudioSystem.getSourceDataLine(formato)
        try {
            line.open(formato)
            line.start()
            RandomAccessFile(wav.toFile(), "r").use { raf -> riproduciDaOffset(raf, line, sequenza, daMs) }
        } finally {
            runCatching { line.drain() }
            line.stop()
            line.close()
        }
    }

    /** Walks [sequenza] in the concatenated coordinate space, skipping whatever is before [daMs],
     * then plays the rest in order — stopping at the end, or as soon as [pausa] interrupts. */
    private fun riproduciDaOffset(
        raf: RandomAccessFile,
        line: SourceDataLine,
        sequenza: List<Pair<Long, Long>>,
        daMs: Long,
    ) {
        var percorsi = 0L
        for ((inizio, fine) in sequenza) {
            val fineTratto = percorsi + (fine - inizio)
            if (fineTratto <= daMs) {
                percorsi = fineTratto
                continue
            }
            val salto = maxOf(0L, daMs - percorsi)
            val interrotto = riproduciIntervallo(raf, line, inizio + salto, fine, percorsi + salto)
            percorsi = fineTratto
            if (interrotto) return
        }
    }

    /** Plays wav bytes for `[inizioMs, fineMs)` (WAV/file coordinates); [partenzaSequenza] is where
     * [inizioMs] sits in the sequence coordinate space, to keep [posizione] in that space too.
     * @return true if [pausa] interrupted playback before [fineMs]. */
    private fun riproduciIntervallo(
        raf: RandomAccessFile,
        line: SourceDataLine,
        inizioMs: Long,
        fineMs: Long,
        partenzaSequenza: Long,
    ): Boolean {
        raf.seek(byteDelCampione(inizioMs))
        var ms = inizioMs
        while (ms < fineMs) {
            if (interrompi) return true
            ms = riproduciBlocco(raf, line, ms, fineMs)
            posizione = partenzaSequenza + (ms - inizioMs)
        }
        return false
    }

    /** Plays one up-to-[BLOCCO_MS] chunk starting at [ms]; past the end of the WAV, silence. @return the new `ms`. */
    private fun riproduciBlocco(raf: RandomAccessFile, line: SourceDataLine, ms: Long, fineMs: Long): Long {
        val fineBlocco = minOf(ms + BLOCCO_MS, fineMs)
        val byteBlocco = ((fineBlocco - ms) * CAMPIONI_PER_MS * BYTE_PER_CAMPIONE).toInt()
        val buffer = ByteArray(byteBlocco)
        val letti = raf.read(buffer).coerceAtLeast(0)
        if (letti < byteBlocco) buffer.fill(0, letti, byteBlocco)
        line.write(buffer, 0, byteBlocco)
        return fineBlocco
    }

    private companion object {
        const val BIT_PER_CAMPIONE = 16
        const val BLOCCO_MS = 100L
        const val ATTESA_ARRESTO_MS = 2_000L
    }
}
