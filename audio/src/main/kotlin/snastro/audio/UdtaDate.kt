package snastro.audio

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Path

/**
 * ADR 0040 §2: the text of the ISO-BMFF box `moov/udta/date` (what Voice Memos writes), or `null`.
 * Read-only and bounded: it walks box headers (32-bit size, 64-bit `largesize`, size 0 = to the end of
 * the file) with seeks, so `mdat` is skipped and never read; only the small `date` body is read into memory.
 * Anything malformed (truncated box, size overflow, missing `moov`/`udta`/`date`, unreadable file)
 * counts as absent — it never throws, so it can never fail an import.
 */
internal fun leggiUdtaDate(file: Path): String? = try {
    RandomAccessFile(file.toFile(), "r").use { raf ->
        val moov = trova(raf, 0, raf.length(), "moov")
        val udta = moov?.let { trova(raf, it.first, it.second, "udta") }
        val date = udta?.let { trova(raf, it.first, it.second, "date") }
        date?.let { corpo(raf, it) }
    }
} catch (@Suppress("SwallowedException") illeggibile: IOException) {
    null
}

private const val LUNGHEZZA_TIPO = 4
private const val MASCHERA_32_BIT = 0xffffffffL
private const val DIMENSIONE_ESTESA = 1L
private const val FINO_ALLA_FINE = 0L
private const val INTESTAZIONE = 8L
private const val INTESTAZIONE_ESTESA = 16L
private const val MASSIMO_CORPO_DATE = 256L

/** The body range `[first, second)` of the first box [tipo] among the children in `[inizio, fine)`, or `null`. */
private fun trova(raf: RandomAccessFile, inizio: Long, fine: Long, tipo: String): Pair<Long, Long>? {
    var pos = inizio
    var trovato: Pair<Long, Long>? = null
    var valido = true
    while (valido && trovato == null && pos + INTESTAZIONE <= fine) {
        raf.seek(pos)
        var dimensione = raf.readInt().toLong() and MASCHERA_32_BIT
        val nome = ByteArray(LUNGHEZZA_TIPO).also(raf::readFully).toString(Charsets.ISO_8859_1)
        var intestazione = INTESTAZIONE
        if (dimensione == DIMENSIONE_ESTESA) {
            dimensione = raf.readLong()
            intestazione = INTESTAZIONE_ESTESA
        } else if (dimensione == FINO_ALLA_FINE) {
            dimensione = fine - pos
        }
        valido = dimensione >= intestazione && dimensione <= fine - pos // else: overflow, truncated or negative
        if (valido && nome == tipo) trovato = (pos + intestazione) to (pos + dimensione)
        pos += dimensione
    }
    return trovato
}

private fun corpo(raf: RandomAccessFile, corpo: Pair<Long, Long>): String? {
    val lunghezza = corpo.second - corpo.first
    if (lunghezza <= 0 || lunghezza > MASSIMO_CORPO_DATE) return null
    raf.seek(corpo.first)
    return ByteArray(lunghezza.toInt()).also(raf::readFully).toString(Charsets.UTF_8).trim { it <= ' ' }
}
