package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreVoci] over Published Language data (passes [LettoreVociContratto]), reading
 * [voci] and [segmenti] live: a Registrazione absent from a map has no Trascritto for that method
 * (INV-5) — a caller that only cares about [voci] may leave [segmenti] at its default. [voci]
 * returns the Voci by voceId and each Voce's intervalli by inizio, whatever order they were given in
 * (intervalli with the same inizio keep their given order). [segmenti] returns every entry ordered by
 * inizio then segmentoId (ADR 0019 §4.1), regardless of the given order.
 */
public class LettoreVociFinta(
    private val voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap(),
    private val segmenti: Map<RegistrazioneId, List<SegmentoDiVoce>> = emptyMap(),
) : LettoreVoci {
    override fun voci(id: RegistrazioneId): List<VoceVista>? =
        voci[id]
            ?.sortedBy { it.voceRef.voceId.numero }
            ?.map { v -> v.copy(intervalli = v.intervalli.sortedBy { it.inizioMs }) }

    override fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce>? =
        segmenti[id]?.sortedWith(compareBy({ it.intervallo.inizioMs }, { it.segmentoId.numero }))
}
