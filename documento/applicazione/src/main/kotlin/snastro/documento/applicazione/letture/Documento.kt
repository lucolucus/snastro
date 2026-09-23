package snastro.documento.applicazione.letture

import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
     * `"<AAAA-MM-DD> <titolo>.md"` (ADR 0010) — pure and deterministic, so `rigenerazione-documento`
     * can compare the name before/after a `DataRegistrazioneModificata` without writing anything.
     */
    public fun nomeFile(dataRegistrazione: LocalDate, titolo: String): String =
        "${dataRegistrazione.format(FORMATO_NOME_FILE)} $titolo.md"

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

    private val FORMATO_NOME_FILE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val FORMATO_VISUALIZZATO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}
