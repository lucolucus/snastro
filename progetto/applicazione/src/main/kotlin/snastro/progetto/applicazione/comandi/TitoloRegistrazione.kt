package snastro.progetto.applicazione.comandi

import java.text.Normalizer
import java.util.Locale

/**
 * The titolo a new Registrazione receives in `AggiungiRegistrazione` (AC-322..324), and the key
 * `RinominaRegistrazione` checks (AC-361): unique in its
 * Progetto on the file-safe, case-insensitive [chiave], so the Documento file name
 * `nomeFile(data, titolo)` of two Registrazioni can never collide (`tec-scrittore-documento`
 * keys.nomeFile). Pure; `internal` so the rule is table-tested directly.
 */
internal object TitoloRegistrazione {

    /**
     * The first of `base`, `base (2)`, `base (3)`… whose [chiave] is not the key of any of
     * [titoliEsistenti] (the titoli of the SAME Progetto). Before a suffix is appended the base is
     * truncated to [LIMITE_BYTE_PULITO] UTF-8 bytes minus the suffix's length (code-point boundary,
     * then trailing spaces/dots removed), so the suffix always survives [pulisci] and the search
     * terminates (AC-324). Deterministic: same inputs → same titolo (AC-323).
     */
    fun unico(base: String, titoliEsistenti: Collection<String>): String {
        val occupate = titoliEsistenti.mapTo(HashSet()) { chiave(it) }
        if (chiave(base) !in occupate) return base
        return generateSequence(PRIMO_SUFFISSO) { it + 1 }
            .map { n ->
                val suffisso = " ($n)" // ASCII: its length in chars is its length in UTF-8 bytes
                rifilaFine(troncaAByte(base, LIMITE_BYTE_PULITO - suffisso.length)) + suffisso
            }
            .first { chiave(it) !in occupate }
    }

    /**
     * The uniqueness key of a titolo (AC-322): `pulisci(t)` case-folded with [Locale.ROOT] —
     * uppercased THEN lowercased, so letters a case-insensitive filesystem folds together (e.g. the
     * Greek final sigma: `ας` / `ασ` / `ΑΣ`) get one key, not two.
     */
    fun chiave(titolo: String): String = pulisci(titolo).uppercase(Locale.ROOT).lowercase(Locale.ROOT)

    /**
     * PRIVATE COPY of documento's `pulisci` (`:progetto` may not depend on `:documento`) — the rule
     * pinned in `tec-scrittore-documento` keys.nomeFile, same table rows as documento AC-320:
     * NFC-normalize; every character invalid on Windows/macOS/Linux (`< > : " / \ | ? *` and the
     * control characters U+0000-U+001F, U+007F) → `_`; leading/trailing spaces and dots removed;
     * truncated to [LIMITE_BYTE_PULITO] UTF-8 bytes on a code-point boundary (never splitting a
     * surrogate pair) and trailing spaces/dots removed again; a Windows reserved name (`CON`, `PRN`,
     * `AUX`, `NUL`, `COM1`-`COM9`, `LPT1`-`LPT9`, case-insensitive) gets a trailing `_`; empty result
     * → `"registrazione"`. Keep it row-for-row identical to documento's.
     */
    fun pulisci(titolo: String): String {
        val normalizzato = Normalizer.normalize(titolo, Normalizer.Form.NFC)
        val sostituito = buildString {
            for (carattere in normalizzato) {
                append(if (carattere.eNonValido()) '_' else carattere)
            }
        }
        val rifilato = rifilaSpaziEPunti(troncaAByte(rifilaSpaziEPunti(sostituito), LIMITE_BYTE_PULITO))
        val conSuffisso = if (rifilato.lowercase(Locale.ROOT) in NOMI_RISERVATI_WINDOWS) "${rifilato}_" else rifilato
        return conSuffisso.ifEmpty { "registrazione" }
    }

    private fun Char.eNonValido(): Boolean =
        this in CARATTERI_NON_VALIDI || code in 0..CONTROLLO_MASSIMO || code == DEL

    private fun rifilaSpaziEPunti(testo: String): String = testo.trim { it == ' ' || it == '.' }

    private fun rifilaFine(testo: String): String = testo.trimEnd { it == ' ' || it == '.' }

    /** Truncates [testo] to at most [limiteByte] UTF-8 bytes, stopping on a code-point boundary. */
    private fun troncaAByte(testo: String, limiteByte: Int): String {
        val risultato = StringBuilder()
        var byteUsati = 0
        var indice = 0
        while (indice < testo.length) {
            val puntoCodice = testo.codePointAt(indice)
            val byteCarattere = byteUtf8(puntoCodice)
            if (byteUsati + byteCarattere > limiteByte) break
            risultato.appendCodePoint(puntoCodice)
            byteUsati += byteCarattere
            indice += Character.charCount(puntoCodice)
        }
        return risultato.toString()
    }

    private fun byteUtf8(puntoCodice: Int): Int = when {
        puntoCodice <= LIMITE_UTF8_1_BYTE -> 1
        puntoCodice <= LIMITE_UTF8_2_BYTE -> 2
        puntoCodice <= LIMITE_UTF8_3_BYTE -> BYTE_UTF8_3
        else -> BYTE_UTF8_4
    }

    /** The first ordinal tried on a clash: the second Registrazione with that key is `base (2)`. */
    private const val PRIMO_SUFFISSO = 2

    /** 255 (NTFS/APFS/ext4 limit) − 11 ("AAAA-MM-DD ") − 3 (".md") − 4 (".tmp" of the atomic write). */
    private const val LIMITE_BYTE_PULITO = 237

    /** U+0000-U+001F (C0 control range) and U+007F (DEL) are invalid on every filesystem. */
    private const val CONTROLLO_MASSIMO = 0x1F
    private const val DEL = 0x7F

    /** UTF-8 byte-length thresholds per code point (1/2/3/4-byte encodings). */
    private const val LIMITE_UTF8_1_BYTE = 0x7F
    private const val LIMITE_UTF8_2_BYTE = 0x7FF
    private const val LIMITE_UTF8_3_BYTE = 0xFFFF
    private const val BYTE_UTF8_3 = 3
    private const val BYTE_UTF8_4 = 4

    private const val PRIMO_NUMERO_COM_LPT = 1
    private const val ULTIMO_NUMERO_COM_LPT = 9

    private val CARATTERI_NON_VALIDI = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
    private val NOMI_RISERVATI_WINDOWS = setOf("con", "prn", "aux", "nul") +
        (PRIMO_NUMERO_COM_LPT..ULTIMO_NUMERO_COM_LPT).map { "com$it" } +
        (PRIMO_NUMERO_COM_LPT..ULTIMO_NUMERO_COM_LPT).map { "lpt$it" }
}
