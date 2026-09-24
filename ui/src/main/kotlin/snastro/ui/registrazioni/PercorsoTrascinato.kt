package snastro.ui.registrazioni

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val RADICE_ESADECIMALE = 16
private const val LUNGHEZZA_ESCAPE_PERCENTO = 2 // "%XX": 2 hex digits after '%'

// L478e: `File.toURI().toString()` for a Windows path yields `file:/C:/Users/...` — a single leading
// slash directly followed by a drive letter and ':'. Never matches a real Unix/macOS path (whose first
// segment is never one letter followed by ':'), so this is safe cross-platform.
private val LETTERA_UNITA_WINDOWS = Regex("^/[A-Za-z]:/.*")

/**
 * H1: turns a `file:` URI (as `DragData.FilesList.readFiles()` hands it, e.g.
 * `File.toURI().toString()`) into the real filesystem path. `internal` + moved out of the composable
 * file (RC-2 thin view) specifically so it is unit-tested on its own ([PercorsoTrascinatoTest]) — the
 * previous version mangled every non-ASCII path (accented letters, mixed scripts) because it decoded
 * one [Char] at a time via `.code`, truncating anything above a single byte instead of decoding the
 * URI's percent-escapes as UTF-8 bytes. Returns `null` (never throws) on a malformed `%` escape, an
 * invalid UTF-8 byte sequence (L485d) or an unrecognized scheme — the caller (an AWT drag-and-drop
 * callback) must not propagate an exception.
 */
internal fun percorsoDaUriFile(uri: String): String? {
    if (!uri.startsWith("file:")) return null
    val senzaSchema = uri.removePrefix("file:")
    val percorso = when {
        senzaSchema.startsWith("//") -> senzaSchema.substring(1)
        senzaSchema.matches(LETTERA_UNITA_WINDOWS) -> senzaSchema.substring(1) // L478e
        else -> senzaSchema
    }
    return decodificaPercentoUri(percorso)
}

/**
 * Reverses RFC 3986 percent-encoding: a run of plain (non-`%`) characters is copied as UTF-8 bytes in
 * one shot (`String.encodeToByteArray()`, which correctly handles surrogate pairs — chars beyond the
 * BMP), each `%xx` escape contributes exactly the one byte it encodes, and the whole byte buffer is
 * decoded as UTF-8 only ONCE at the end (H1) — never char-by-char, which mangles anything non-ASCII
 * (e.g. `à` or `é`) because a `Char`'s `.code` can exceed one byte. A `+` is copied verbatim (RFC 3986
 * path/query segments never treat it as a space; that is an `application/x-www-form-urlencoded`
 * convention this is not). Returns `null` on a `%` not followed by two valid hex digits — a caller
 * decision, never a thrown exception (H1).
 */
internal fun decodificaPercentoUri(testo: String): String? {
    val byte = ByteArrayOutputStream()
    var i = 0
    while (i < testo.length) {
        if (testo[i] != '%') {
            val fine = testo.indexOf('%', i).takeIf { it != -1 } ?: testo.length
            byte.write(testo.substring(i, fine).encodeToByteArray())
            i = fine
            continue
        }
        val valore = decodificaEscape(testo, i) ?: return null
        byte.write(valore)
        i += 1 + LUNGHEZZA_ESCAPE_PERCENTO
    }
    return decodificaUtf8Rigoroso(byte.toByteArray())
}

/**
 * The one byte a `%xx` escape starting at [i] encodes, or `null` if malformed. L485d: [Character.digit]
 * per character — never `String.toIntOrNull(16)` on the two-character substring, which accepts a
 * leading sign (`"+1".toIntOrNull(16) == 1`) and would wrongly decode a malformed `%+1` escape.
 */
private fun decodificaEscape(testo: String, i: Int): Int? {
    if (i + LUNGHEZZA_ESCAPE_PERCENTO >= testo.length) return null
    val cifraAlta = Character.digit(testo[i + 1], RADICE_ESADECIMALE)
    val cifraBassa = Character.digit(testo[i + 2], RADICE_ESADECIMALE)
    return if (cifraAlta < 0 || cifraBassa < 0) null else cifraAlta * RADICE_ESADECIMALE + cifraBassa
}

/**
 * L485d: a STRICT UTF-8 decode — `null` on a malformed/incomplete byte sequence — never the JDK's
 * default silent replacement with U+FFFD ([ByteArray.toString]'s behaviour), which would otherwise turn
 * a corrupted drag-and-drop path into a DIFFERENT (wrong) existing path instead of failing the drop.
 */
private fun decodificaUtf8Rigoroso(byte: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(byte))
        .toString()
} catch (
    // H1/L485d: malformed input is an expected outcome here (a caller decision, per this function's own
    // kdoc), not a fault to propagate — the caller only needs to know decoding failed, never why.
    @Suppress("SwallowedException") e: CharacterCodingException,
) {
    null
}
