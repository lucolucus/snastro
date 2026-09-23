package snastro.audio

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin

/**
 * Writes a synthetic 16 kHz mono 16-bit PCM WAV (a sine tone) — the exact shape [DecodificaFfmpeg]
 * itself writes ([FormatoWav]) — so tests exercise real, non-mocked production code
 * ([DecodificaFfmpeg.leggiCampioni], [RiproduttoreWav], and — probed by FFmpeg — [SondaFfmpeg])
 * without needing FFmpeg to CREATE the fixture, and without a committed audio sample (the profile
 * forbids committing one; `sample/` is never in the repo).
 */
internal fun scriviWavSintetico(destinazione: Path, durataMs: Long, frequenzaHz: Double = AMPIEZZA_FREQUENZA_DEFAULT) {
    val campioni = (durataMs * CAMPIONI_PER_MS).toInt()
    val dati = ByteBuffer.allocate(campioni * BYTE_PER_CAMPIONE).order(ByteOrder.LITTLE_ENDIAN)
    repeat(campioni) { i ->
        val secondi = i.toDouble() / FREQUENZA_CAMPIONAMENTO_HZ
        val valore = AMPIEZZA_TONO * sin(2 * PI * frequenzaHz * secondi)
        dati.putShort(valore.toInt().toShort())
    }
    destinazione.parent?.let(java.nio.file.Files::createDirectories)
    RandomAccessFile(destinazione.toFile(), "rw").use { raf ->
        raf.setLength(0)
        raf.write(intestazioneWav(dati.capacity().toLong()))
        raf.write(dati.array())
    }
}

private const val AMPIEZZA_FREQUENZA_DEFAULT = 440.0
private const val AMPIEZZA_TONO = Short.MAX_VALUE * 0.5
