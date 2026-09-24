package snastro.parlanti.adattatori.porte

import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreVoci] over Trascrizione's public read API [VociDelTrascritto] (boundary `voci-per-parlanti`,
 * ADR 0002): calls the supplier's `voci`/`segmentiDiVoce` and maps their answer field by field into
 * this context's own [VoceVista]/[SegmentoDiVoce] — delegates, never re-decides. Both suppliers'
 * methods already carry their pinned order (Voci by voceId, each Voce's intervalli by inizio then
 * segmentoId — AC-98; segmenti by inizio then segmentoId — AC-550): copied over as-is, no re-sorting
 * here, and never the text (ADR 0019 §4.1).
 */
public class LettoreVociDaTrascrizione(
    private val trascrizione: VociDelTrascritto,
) : LettoreVoci {
    override fun voci(id: RegistrazioneId): List<VoceVista>? =
        trascrizione.voci(id)?.map { VoceVista(voceRef = it.voceRef, intervalli = it.intervalli) }

    override fun segmenti(id: RegistrazioneId): List<SegmentoDiVoce>? =
        trascrizione.segmentiDiVoce(id)?.map {
            SegmentoDiVoce(it.segmentoId, it.voceId, it.intervallo, it.confermato)
        }
}
