package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId

/**
 * In-memory [LettoreVoci] over Published Language data (passes [LettoreVociContratto]), reading
 * [voci] and [segmenti] live: an Incontro absent from a map has no transcribed Parte for that method
 * (INV-5) — a caller that only cares about [voci] may leave [segmenti] at its default. [voci]
 * returns the Voci by voceId and each Voce's intervalli in each Parte by inizio, whatever order they were given in
 * (intervalli with the same inizio keep their given order). [segmenti] returns each Parte's entries together, in the
 * order the Parti first appear, each Parte's ordered by inizio then segmentoId (ADR 0019 §4.1).
 */
public class LettoreVociFinta(
    private val voci: Map<IncontroId, List<VoceVista>> = emptyMap(),
    private val segmenti: Map<IncontroId, List<SegmentoDiVoce>> = emptyMap(),
) : LettoreVoci {
    override fun voci(incontroId: IncontroId): List<VoceVista>? =
        voci[incontroId]
            ?.sortedBy { it.voceRef.voceId.numero }
            ?.map { v -> v.copy(intervalliPerParte = v.intervalliPerParte.mapValues { (_, i) -> i.sortedBy { it.inizioMs } }) }

    override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce>? =
        segmenti[incontroId]
            ?.groupBy { it.segmento.registrazioneId }
            ?.values
            ?.flatMap { parte -> parte.sortedWith(compareBy({ it.intervallo.inizioMs }, { it.segmento.segmentoId.numero })) }
}
