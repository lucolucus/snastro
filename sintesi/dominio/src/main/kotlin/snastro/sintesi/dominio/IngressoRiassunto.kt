package snastro.sintesi.dominio

import snastro.kernel.VoceId

/**
 * Builds the labelled LLM input (ADR 0021 §4 as amended 2026-09-26, pure): one line `[s<segmentoId> V<voceId>] <testo>`
 * per Segmento in the given order, with no timestamp ([SegmentoIngresso.inizioMs] is not written), then a legend
 * `V<n> = <Nome | Voce n>` per Voce of the input, ascending by n. Names label the input only: they are never
 * stored (INV-S5).
 */
public object IngressoRiassunto {
    public fun costruisci(segmenti: List<SegmentoIngresso>, nomi: Map<VoceId, String>): String {
        val righe = segmenti.map { "[s${it.segmentoId.numero} V${it.voceId.numero}] ${it.testo}" }
        val legenda = segmenti.map { it.voceId }.distinct().sortedBy { it.numero }
            .map { v -> "V${v.numero} = ${nomi[v] ?: "Voce ${v.numero}"}" }
        return (righe + legenda).joinToString("\n")
    }
}
