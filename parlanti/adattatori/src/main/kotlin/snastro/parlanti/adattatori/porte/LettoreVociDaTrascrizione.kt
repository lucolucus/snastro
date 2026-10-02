package snastro.parlanti.adattatori.porte

import snastro.kernel.IncontroId
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreVoci] over Trascrizione's public read API [VociDelTrascritto] (boundary `voci-per-parlanti`,
 * ADR 0002): calls the supplier's `voci(incontroId)`/`segmenti(incontroId)` and maps their answer field by field
 * into this context's own [VoceVista]/[SegmentoDiVoce] — delegates, never re-decides. The supplier already carries
 * the pinned order (Voci by voceId, intervals per Parte in Parte order, Segmenti in Parte order then inizio): copied
 * over as-is, no re-sorting here, and never the text (ADR 0019 §4.1).
 */
public class LettoreVociDaTrascrizione(
    private val trascrizione: VociDelTrascritto,
) : LettoreVoci {
    override fun voci(incontroId: IncontroId): List<VoceVista>? =
        trascrizione.voci(incontroId)?.map { VoceVista(it.voceRef, it.intervalliPerParte) }

    override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce>? =
        trascrizione.segmenti(incontroId)?.map { SegmentoDiVoce(it.segmento, it.voceId, it.intervallo, it.confermato) }
}
