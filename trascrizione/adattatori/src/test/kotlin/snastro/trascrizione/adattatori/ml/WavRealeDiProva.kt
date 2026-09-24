package snastro.trascrizione.adattatori.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal WAV (canonical RIFF/PCM16) reader + linear resample to 16 kHz mono — test-only (AC-252
 * needs REAL speech, never a synthetic tone: `RiconoscitoreParlatoContratto`'s default `parlato()`
 * would not do). No `javax.sound` (CR-3 confines it to `:audio`): a few dozen lines of manual parsing
 * are enough for the small, canonical `.wav` files shipped inside the Parakeet archive's `test_wavs`.
 */
internal fun wav16kMono(percorso: Path): FloatArray {
    val buf = ByteBuffer.wrap(Files.readAllBytes(percorso)).order(ByteOrder.LITTLE_ENDIAN)
    require(leggiTag(buf) == "RIFF") { "non è un file RIFF/WAV: $percorso" }
    buf.int // dimensione RIFF, ignorata
    require(leggiTag(buf) == "WAVE") { "non è un file WAVE: $percorso" }

    var canali = 1
    var frequenzaHz = 16_000
    var bitPerCampione = 16
    var dati: FloatArray? = null
    while (buf.remaining() >= MINIMO_INTESTAZIONE_CHUNK) {
        val id = leggiTag(buf)
        val dimensione = buf.int
        val inizio = buf.position()
        when (id) {
            "fmt " -> {
                buf.short // formato audio, ignorato (atteso PCM)
                canali = buf.short.toInt()
                frequenzaHz = buf.int
                buf.int // byteRate, ignorato
                buf.short // blockAlign, ignorato
                bitPerCampione = buf.short.toInt()
            }
            "data" -> {
                require(bitPerCampione == BIT_PER_CAMPIONE_ATTESI) { "solo PCM16 e supportato: $percorso" }
                val campioniPerCanale = dimensione / 2 / canali
                dati = FloatArray(campioniPerCanale) { i ->
                    buf.getShort(inizio + i * canali * 2) / AMPIEZZA_MASSIMA_PCM16
                }
            }
        }
        buf.position(inizio + dimensione + (dimensione and 1)) // i chunk sono allineati a 2 byte
    }
    val campioni = requireNotNull(dati) { "nessun chunk 'data' in $percorso" }
    return ricampiona(campioni, frequenzaHz, CAMPIONAMENTO_ATTESO_HZ)
}

private fun leggiTag(buf: ByteBuffer): String {
    val tag = ByteArray(4)
    buf.get(tag)
    return String(tag, Charsets.US_ASCII)
}

/** Interpolazione lineare: sufficiente per un test di riconoscimento, mai spedita in produzione. */
private fun ricampiona(campioni: FloatArray, da: Int, a: Int): FloatArray {
    if (da == a) return campioni
    val n = (campioni.size.toLong() * a / da).toInt()
    return FloatArray(n) { i ->
        val posizione = i.toDouble() * da / a
        val indice = posizione.toInt().coerceIn(0, campioni.size - 1)
        val successivo = (indice + 1).coerceAtMost(campioni.size - 1)
        val frazione = posizione - indice
        (campioni[indice] * (1 - frazione) + campioni[successivo] * frazione).toFloat()
    }
}

private const val MINIMO_INTESTAZIONE_CHUNK = 8
private const val BIT_PER_CAMPIONE_ATTESI = 16
private const val CAMPIONAMENTO_ATTESO_HZ = 16_000
private const val AMPIEZZA_MASSIMA_PCM16 = 32_768f
