package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreVoci] over Published Language data (passes [LettoreVociContratto]), reading [voci]
 * live. It returns the Voci by voceId and each Voce's intervalli by inizio, whatever order they were
 * given in; intervalli with the same inizio keep their given order (the caller gives them by segmentoId).
 */
public class LettoreVociFinta(
    private val voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap(),
) : LettoreVoci {
    override fun voci(id: RegistrazioneId): List<VoceVista>? =
        voci[id]
            ?.sortedBy { it.voceRef.voceId.numero }
            ?.map { v -> v.copy(intervalli = v.intervalli.sortedBy { it.inizioMs }) }
}
