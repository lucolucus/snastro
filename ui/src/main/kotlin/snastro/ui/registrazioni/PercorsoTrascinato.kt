package snastro.ui.registrazioni

import java.io.ByteArrayOutputStream

private const val RADICE_ESADECIMALE = 16
private const val LUNGHEZZA_ESCAPE_PERCENTO = 2 // "%XX": 2 hex digits after '%'

/**
 * H1: turns a `file:` URI (as `DragData.FilesList.readFiles()` hands it, e.g.
 * `File.toURI().toString()`) into the real filesystem path. `internal` + moved out of the composable
 * file (RC-2 thin view) specifically so it is unit-tested on its own ([PercorsoTrascinatoTest]) — the
 * previous version mangled every non-ASCII path (accented letters, mixed scripts) because it decoded
 * one [Char] at a time via `.code`, truncating anything above a single byte instead of decoding the
 * URI's percent-escapes as UTF-8 bytes. Returns `null` (never throws) on a malformed `%` escape or an
 * unrecognized scheme — the caller (an AWT drag-and-drop callback) must not propagate an exception.
 */
internal fun percorsoDaUriFile(uri: String): String? {
    if (!uri.startsWith("file:")) return null
    val senzaSchema = uri.removePrefix("file:")
    val percorso = if (senzaSchema.startsWith("//")) senzaSchema.substring(1) else senzaSchema
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
    return byte.toByteArray().toString(Charsets.UTF_8)
}

/** The one byte a `%xx` escape starting at [i] encodes, or `null` if malformed. */
private fun decodificaEscape(testo: String, i: Int): Int? {
    if (i + LUNGHEZZA_ESCAPE_PERCENTO >= testo.length) return null
    return testo.substring(i + 1, i + 1 + LUNGHEZZA_ESCAPE_PERCENTO).toIntOrNull(RADICE_ESADECIMALE)
}
