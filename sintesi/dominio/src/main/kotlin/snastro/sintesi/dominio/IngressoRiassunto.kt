package snastro.sintesi.dominio

import snastro.kernel.VoceId

/**
 * Builds the labelled LLM input (ADR 0021 §4, pure): one line `[s<segmentoId> V<voceId> m:ss] <testo>` per
 * Segmento in the given order (`h:mm:ss` from one hour), then a legend `V<n> = <Nome | Voce n>` per Voce of the
 * input, ascending by n. Names label the input only: they are never stored (INV-S5).
 */
public object IngressoRiassunto {
    private const val MS_AL_SECONDO = 1_000L
    private const val SECONDI_AL_MINUTO = 60L
    private const val SECONDI_ALL_ORA = 3_600L

    public fun costruisci(segmenti: List<SegmentoIngresso>, nomi: Map<VoceId, String>): String {
        val righe = segmenti.map { "[s${it.segmentoId.numero} V${it.voceId.numero} ${tempo(it.inizioMs)}] ${it.testo}" }
        val legenda = segmenti.map { it.voceId }.distinct().sortedBy { it.numero }
            .map { v -> "V${v.numero} = ${nomi[v] ?: "Voce ${v.numero}"}" }
        return (righe + legenda).joinToString("\n")
    }

    private fun tempo(ms: Long): String {
        val secondi = ms / MS_AL_SECONDO
        val ore = secondi / SECONDI_ALL_ORA
        val minuti = secondi % SECONDI_ALL_ORA / SECONDI_AL_MINUTO
        val ss = (secondi % SECONDI_AL_MINUTO).toString().padStart(2, '0')
        return if (ore > 0) "$ore:${minuti.toString().padStart(2, '0')}:$ss" else "$minuti:$ss"
    }
}
