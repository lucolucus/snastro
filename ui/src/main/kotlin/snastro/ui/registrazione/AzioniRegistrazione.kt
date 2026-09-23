package snastro.ui.registrazione

import snastro.kernel.SegmentoId

/**
 * One lambda per user action of S3 · Registrazione, READ-ONLY in R1 (dev-architecture `#presenter`,
 * user decision K-c). [riproduciDaInizio]/[pausa] back the header audio bar; [riproduciSegmento] is
 * AC-208's click/'▶' on a Segmento (a no-op when the audio source is missing, AC-217 — enforced by the
 * presenter, not just by disabling the control). No selection, no Revisione action here (AC-402).
 */
data class AzioniRegistrazione(
    val riproduciDaInizio: () -> Unit,
    val pausa: () -> Unit,
    val riproduciSegmento: (SegmentoId) -> Unit,
    val apriDocumento: () -> Unit,
    val mostraDocumentoNellaCartella: () -> Unit,
    val chiudiErrore: () -> Unit,
    val riprova: () -> Unit,
)
