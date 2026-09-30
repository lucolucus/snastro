package snastro.sintesi.dominio

/**
 * Builds the labelled LLM input (ADR 0021 §4 as amended 2026-09-26, pure): one line `[s<segmentoId> V<voceId>] <testo>`
 * per Segmento in the given order, with no timestamp ([SegmentoIngresso.inizioMs] is not written), then a legend
 * `V<n> = Voce n` per Voce of the input, ascending by n. It takes no Nomi at all (ADR 0032): with names in the legend
 * the model returned no elements; Nomi are applied only when the Riassunto is shown.
 */
public object IngressoRiassunto {
    public fun costruisci(segmenti: List<SegmentoIngresso>): String {
        val righe = segmenti.map { "[s${it.segmentoId.numero} V${it.voceId.numero}] ${it.testo}" }
        val legenda = segmenti.map { it.voceId }.distinct().sortedBy { it.numero }
            .map { v -> "V${v.numero} = Voce ${v.numero}" }
        return (righe + legenda).joinToString("\n")
    }
}
