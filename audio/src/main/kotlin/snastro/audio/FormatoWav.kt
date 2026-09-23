package snastro.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The one WAV shape `:audio` ever writes or reads (ADR 0005): 16 kHz mono 16-bit PCM, canonical
 * 44-byte header, no extra chunks. [DecodificaFfmpeg] is the only writer, so [DecodificaFfmpeg]'s
 * own reader ([DecodificaFfmpeg.leggiCampioni]) never needs a general-purpose WAV parser.
 */
internal const val FREQUENZA_CAMPIONAMENTO_HZ: Int = 16_000
internal const val CAMPIONI_PER_MS: Int = FREQUENZA_CAMPIONAMENTO_HZ / 1_000
internal const val BYTE_PER_CAMPIONE: Int = 2
internal const val INTESTAZIONE_WAV_BYTE: Long = 44L

/** Bytes of the `fmt ` subchunk body for PCM (audioFormat..bitsPerSample, no extension). */
private const val DIMENSIONE_SUBCHUNK_FMT_PCM = 16

/** `RIFF` chunk-size field excludes its own 8 bytes (`"RIFF"` + the size field itself). */
private const val BYTE_NON_CONTATI_NEL_CHUNK_SIZE = 8

private const val CODICE_FORMATO_PCM: Short = 1
private const val NUMERO_CANALI_MONO: Short = 1

/** The 44-byte canonical header for [dataSize] bytes of 16 kHz mono 16-bit PCM samples. */
internal fun intestazioneWav(dataSize: Long): ByteArray {
    val byteRate = FREQUENZA_CAMPIONAMENTO_HZ * BYTE_PER_CAMPIONE
    val bitPerCampione = (BYTE_PER_CAMPIONE * java.lang.Byte.SIZE).toShort()
    return ByteBuffer.allocate(INTESTAZIONE_WAV_BYTE.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt((INTESTAZIONE_WAV_BYTE - BYTE_NON_CONTATI_NEL_CHUNK_SIZE + dataSize).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(DIMENSIONE_SUBCHUNK_FMT_PCM)
        putShort(CODICE_FORMATO_PCM)
        putShort(NUMERO_CANALI_MONO)
        putInt(FREQUENZA_CAMPIONAMENTO_HZ)
        putInt(byteRate)
        putShort(BYTE_PER_CAMPIONE.toShort()) // blockAlign
        putShort(bitPerCampione)
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataSize.toInt())
    }.array()
}

/** Byte offset, in a canonical WAV written by [intestazioneWav], of the sample at [ms]. */
internal fun byteDelCampione(ms: Long): Long = INTESTAZIONE_WAV_BYTE + ms * CAMPIONI_PER_MS * BYTE_PER_CAMPIONE
