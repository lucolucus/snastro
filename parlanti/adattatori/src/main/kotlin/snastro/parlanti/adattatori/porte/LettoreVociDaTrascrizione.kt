package snastro.parlanti.adattatori.porte

import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreVoci] over Trascrizione's public read API [VociDelTrascritto] (boundary `voci-per-parlanti`,
 * ADR 0002): calls the supplier's `voci(id)` and maps its answer field by field into this context's
 * own [VoceVista] — delegates, never re-decides. [VociDelTrascritto.voci] already orders Voci by
 * voceId and each Voce's intervalli by inizio then segmentoId (its own contract, AC-98): copied over
 * as-is, no re-sorting here.
 */
public class LettoreVociDaTrascrizione(
    private val trascrizione: VociDelTrascritto,
) : LettoreVoci {
    override fun voci(id: RegistrazioneId): List<VoceVista>? =
        trascrizione.voci(id)?.map { VoceVista(voceRef = it.voceRef, intervalli = it.intervalli) }
}
