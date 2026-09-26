package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * The `segmentoId → voceId` assignment of the Trascritto a run read (INV-S4 against it, INV-S7 by its [chiave]).
 * Equal assignments are equal whatever the input order.
 */
public class StrutturaTrascritto private constructor(private val assegnazione: Map<SegmentoId, VoceId>) {
    /** Canonical, collision-free: `<segmentoId>:<voceId>` ordered by segmentoId, joined by `,` (ADR 0022). */
    public val chiave: String =
        assegnazione.entries.sortedBy { it.key.numero }.joinToString(",") { "${it.key.numero}:${it.value.numero}" }

    public val voci: Set<VoceId> get() = assegnazione.values.toSet()

    public fun contiene(segmentoId: SegmentoId): Boolean = segmentoId in assegnazione

    public fun voceDi(segmentoId: SegmentoId): VoceId? = assegnazione[segmentoId]

    override fun equals(other: Any?): Boolean = other is StrutturaTrascritto && other.chiave == chiave

    override fun hashCode(): Int = chiave.hashCode()

    override fun toString(): String = "StrutturaTrascritto($chiave)"

    public companion object {
        public fun di(coppie: List<Pair<SegmentoId, VoceId>>): StrutturaTrascritto {
            val assegnazione = coppie.toMap()
            require(assegnazione.size == coppie.size) { "segmentoId duplicato nella struttura" }
            return StrutturaTrascritto(assegnazione)
        }
    }
}
