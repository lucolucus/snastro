package snastro.trascrizione.applicazione.letture

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * One row of [TrascrittoView.segmenti]: [testo] verbatim, in time order across Voci (AC-167) — the
 * order [snastro.trascrizione.dominio.Trascritto.segmenti] already guarantees (INV-7/INV-8).
 */
public data class SegmentoTrascrittoView(
    val segmentoId: SegmentoId,
    val voceId: VoceId,
    val inizioMs: Long,
    val fineMs: Long,
    val testo: String,
)
