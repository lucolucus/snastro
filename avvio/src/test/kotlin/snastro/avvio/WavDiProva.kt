package snastro.avvio

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared test helper (RC-9/frugality: one WAV writer, not one per test file): writes a tiny synthetic
 * 16 kHz mono 16-bit PCM WAV (silence) — never a committed sample. Used by real-audio-adapter tests
 * that need a source FFmpeg can actually probe/decode (`@Tag("modelli")`).
 */
internal fun scriviWavSintetico(destinazione: Path, durataMs: Long) {
    val frequenzaHz = 16_000
    val bytePerCampione = 2
    val campioni = (durataMs * frequenzaHz / 1_000).toInt()
    val dati = ByteArray(campioni * bytePerCampione)
    destinazione.parent?.let(Files::createDirectories)
    RandomAccessFile(destinazione.toFile(), "rw").use { raf ->
        raf.setLength(0)
        raf.write(intestazioneWavDiProva(dati.size.toLong(), frequenzaHz, bytePerCampione))
        raf.write(dati)
    }
}

private fun intestazioneWavDiProva(dataSize: Long, frequenzaHz: Int, bytePerCampione: Int): ByteArray {
    val byteRate = frequenzaHz * bytePerCampione
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((36 + dataSize).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1) // PCM
        putShort(1) // mono
        putInt(frequenzaHz)
        putInt(byteRate)
        putShort(bytePerCampione.toShort())
        putShort((bytePerCampione * 8).toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataSize.toInt())
    }.array()
}
