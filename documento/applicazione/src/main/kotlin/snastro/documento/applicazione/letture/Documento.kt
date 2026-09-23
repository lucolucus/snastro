package snastro.documento.applicazione.letture

import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The `documento` read-model: projects a [TrascrittoTesto] (`trascritto-per-documento`) and the
 * Nomi of its attributed Voci (`nomi-per-documento`) into a [DocumentoVista] — pure, no I/O.
 * [INV-23]: same inputs → byte-identical output; the `.md` is never read back by this block.
 * Fetching the inputs (via `LettoreTrascritto`/`LettoreNomi`) and writing the result (via
 * `ScrittoreDocumento`) are the caller's job (`rigenerazione-documento`, wave 5).
 */
public object Documento {

    /**
     * Projects [trascritto] and [nomi] (the Nome of each attributed Voce, keyed by [VoceRef]; a Voce
     * absent from the map has no Attribuzione) into a [DocumentoVista].
     *
     * Format: `# <titolo>`, a blank line, `Registrata il dd/MM/yyyy`, a blank line, then one line
     * `**Nome** (mm:ss): testo` per Segmento followed by a blank line (so two consecutive Segmenti
     * of the same Voce stay separate lines). `mm` is the Segmento's start expressed as total
     * minutes — it does not wrap at 60. [INV-24]: a Voce without Attribuzione renders as "Voce n"
     * (n = its [snastro.kernel.VoceId.numero]); an eliminato Parlante's Voce still renders with its
     * Nome, because [nomi] already resolves it (`LettoreNomi`, [INV-24]). Segmenti render in time
     * order across Voci (`intervallo.inizioMs`, a parità `segmentoId`) regardless of the order they
     * arrive in [trascritto.segmenti] — the projection never trusts an unsorted input.
     */
    public fun proietta(trascritto: TrascrittoTesto, nomi: Map<VoceRef, String>): DocumentoVista {
        val segmentiInOrdine = trascritto.segmenti.sortedWith(
            compareBy({ it.intervallo.inizioMs }, { it.segmentoId.numero }),
        )
        val markdown = buildString {
            append("# ${trascritto.titolo}\n\n")
            append("Registrata il ${trascritto.dataRegistrazione.format(FORMATO_VISUALIZZATO)}\n\n")
            for (segmento in segmentiInOrdine) {
                val nome = nomeVoce(trascritto.registrazioneId, segmento, nomi)
                append("**$nome** (${tempo(segmento)}): ${segmento.testo}\n\n")
            }
        }
        return DocumentoVista(markdown = markdown, nomeFile = nomeFile(trascritto.dataRegistrazione, trascritto.titolo))
    }

    /**
     * `"<AAAA-MM-DD> " + pulisci(titolo) + ".md"` (ADR 0010, `tec-scrittore-documento` keys.nomeFile)
     * — pure and deterministic, so `rigenerazione-documento` can compare the name before/after a
     * `DataRegistrazioneModificata` without writing anything. Unique per Progetto because [titolo]
     * already is (`servizi-registrazione` AC-322..324) — no ordinal is minted here (it would break
     * [INV-23]).
     */
    public fun nomeFile(dataRegistrazione: LocalDate, titolo: String): String =
        "${dataRegistrazione.format(FORMATO_NOME_FILE)} ${pulisci(titolo)}.md"

    /**
     * AC-320/321: the AC-263 sanitizing rule applied to a Documento [titolo] (same table rows,
     * pinned in `tec-scrittore-documento` keys.nomeFile). NFC-normalizes; every character invalid on
     * Windows/macOS/Linux (`< > : " / \ | ? *` and the control characters U+0000-U+001F, U+007F)
     * becomes `_`; leading/trailing spaces and dots are removed; the result is truncated to
     * [LIMITE_BYTE_PULITO] UTF-8 bytes on a code-point boundary (never splitting a surrogate pair)
     * and trailing spaces/dots removed again; a Windows reserved name (`CON`, `PRN`, `AUX`, `NUL`,
     * `COM1`-`COM9`, `LPT1`-`LPT9`, case-insensitive) gets a trailing `_`; an empty result becomes
     * `"registrazione"`. Idempotent: `pulisci(pulisci(t)) == pulisci(t)`. `internal` (table-tested
     * directly): nothing outside this block needs it — the view_shape only exposes `nomeFile`.
     */
    internal fun pulisci(titolo: String): String {
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

    /** Truncates [testo] to at most [limiteByte] UTF-8 bytes, stopping on a code-point boundary. */
    private fun troncaAByte(testo: String, limiteByte: Int): String {
        val risultato = StringBuilder()
        var byteUsati = 0
        var indice = 0
        while (indice < testo.length) {
            val puntoCodice = testo.codePointAt(indice)
            val lunghezzaCarattere = Character.charCount(puntoCodice)
            val byteCarattere = byteUtf8(puntoCodice)
            if (byteUsati + byteCarattere > limiteByte) break
            risultato.appendCodePoint(puntoCodice)
            byteUsati += byteCarattere
            indice += lunghezzaCarattere
        }
        return risultato.toString()
    }

    private fun byteUtf8(puntoCodice: Int): Int = when {
        puntoCodice <= LIMITE_UTF8_1_BYTE -> 1
        puntoCodice <= LIMITE_UTF8_2_BYTE -> 2
        puntoCodice <= LIMITE_UTF8_3_BYTE -> BYTE_UTF8_3
        else -> BYTE_UTF8_4
    }

    private fun nomeVoce(
        registrazioneId: RegistrazioneId,
        segmento: SegmentoVista,
        nomi: Map<VoceRef, String>,
    ): String = nomi[VoceRef(registrazioneId, segmento.voceId)] ?: "Voce ${segmento.voceId.numero}"

    /** `mm:ss` of [SegmentoVista.intervallo]'s start; `mm` grows past 59, `ss` stays zero-padded. */
    private fun tempo(segmento: SegmentoVista): String {
        val secondiTotali = segmento.intervallo.inizioMs / MS_PER_SECONDO
        val minuti = secondiTotali / SECONDI_PER_MINUTO
        val secondi = secondiTotali % SECONDI_PER_MINUTO
        return "$minuti:${secondi.toString().padStart(2, '0')}"
    }

    private const val MS_PER_SECONDO = 1_000L
    private const val SECONDI_PER_MINUTO = 60L

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

    private val FORMATO_NOME_FILE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val FORMATO_VISUALIZZATO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
}
