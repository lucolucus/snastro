package snastro.avvio

import java.util.Locale

/**
 * AC-263: derives the `.snastro` folder name from the `nome` typed for `crea` — a pure function,
 * table-tested. Every character invalid on Windows/macOS/Linux
 * (`< > : " / \ | ? *` and the control characters U+0000-U+001F, U+007F) becomes `_`; leading/
 * trailing spaces and dots are removed (before AND after truncation, so a truncation that lands on
 * one is cleaned up too); the result is truncated to [LUNGHEZZA_MASSIMA_CODE_POINT] code points
 * (never splitting a surrogate pair); a Windows reserved device name (case-insensitive) gets a
 * trailing `_`; an empty result falls back to `progetto`. [SessioneProgettoImpl.crea] appends the
 * `.snastro` suffix and the AC-264 collision suffix (`" ($n)"`) — never part of this function, so
 * both can be inserted between the base and the suffix.
 */
internal object NomeCartella {
    private const val LUNGHEZZA_MASSIMA_CODE_POINT = 60

    /** U+0000-U+001F: the last C0 control code point. */
    private const val ULTIMO_CONTROLLO_C0 = 0x1F

    /** U+007F: DEL, the C1-adjacent control character AC-263 also names explicitly. */
    private const val DEL = 0x7F

    /** The highest BMP code point: above it, `.toChar()` cannot represent [CARATTERI_NON_VALIDI]. */
    private const val ULTIMO_CODE_POINT_BMP = 0xFFFF

    private val CARATTERI_NON_VALIDI = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')

    private val NOMI_RISERVATI_WINDOWS = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    /** The base folder name (WITHOUT the `.snastro` suffix) derived from [nome] (AC-263). */
    fun base(nome: String): String {
        val sostituito = sostituisciCaratteriNonValidi(nome)
        val troncato = ripulisciSpaziEPunti(sostituito).let { troncaACodePoint(it, LUNGHEZZA_MASSIMA_CODE_POINT) }
        val ripulito = ripulisciSpaziEPunti(troncato)
        val base = ripulito.ifEmpty { "progetto" }
        return if (base.uppercase(Locale.ROOT) in NOMI_RISERVATI_WINDOWS) "${base}_" else base
    }

    private fun sostituisciCaratteriNonValidi(testo: String): String {
        val esito = StringBuilder(testo.length)
        testo.codePoints().forEach { cp ->
            val controllo = cp <= ULTIMO_CONTROLLO_C0 || cp == DEL
            val simbolo = cp <= ULTIMO_CODE_POINT_BMP && CARATTERI_NON_VALIDI.contains(cp.toChar())
            if (controllo || simbolo) esito.append('_') else esito.appendCodePoint(cp)
        }
        return esito.toString()
    }

    private fun ripulisciSpaziEPunti(testo: String): String {
        var inizio = 0
        var fine = testo.length
        while (inizio < fine && (testo[inizio] == ' ' || testo[inizio] == '.')) inizio++
        while (fine > inizio && (testo[fine - 1] == ' ' || testo[fine - 1] == '.')) fine--
        return testo.substring(inizio, fine)
    }

    private fun troncaACodePoint(testo: String, massimo: Int): String {
        if (testo.codePointCount(0, testo.length) <= massimo) return testo
        val fine = testo.offsetByCodePoints(0, massimo)
        return testo.substring(0, fine)
    }
}
