package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef

/**
 * Builds the labelled LLM input in ONE pass (INV-I19, ADR 0037 §3, pure): the Parti in the given (INV-I2) order, each
 * Parte's Segmenti in the given (INV-7, time) order, no Parte separator line; one line `[s<k> V<n>] <testo>` per
 * Segmento, k its 1-based position in the whole input and n its Incontro Voce, with no timestamp
 * ([SegmentoIngresso.inizioMs] is not written); then a legend `V<n> = Voce n` per Voce of the input, ascending by n.
 * It takes no Nomi at all (ADR 0032): Nomi are applied only when the Riassunto is shown.
 */
public object IngressoRiassunto {
    public fun costruisci(parti: List<List<SegmentoIngresso>>): IngressoEtichettato {
        val segmenti = parti.flatten()
        require(segmenti.map { it.ref }.toSet().size == segmenti.size) { "Segmento ripetuto nell'ingresso" }
        val righe = segmenti.mapIndexed { i, s -> "[s${i + 1} V${s.voceId.numero}] ${s.testo}" }
        val legenda = segmenti.map { it.voceId }.distinct().sortedBy { it.numero }
            .map { v -> "V${v.numero} = Voce ${v.numero}" }
        return IngressoEtichettato((righe + legenda).joinToString("\n"), segmenti.map { it.ref })
    }
}

/**
 * The input [testo] and its label table: label k ↔ `etichette[k-1]`. The table lives in memory for the run only (key
 * `k`, ADR 0037 §3): it maps the model's `fonti` back to Segmenti ([Riassunto.completa]).
 */
public data class IngressoEtichettato(val testo: String, val etichette: List<SegmentoRef>)
