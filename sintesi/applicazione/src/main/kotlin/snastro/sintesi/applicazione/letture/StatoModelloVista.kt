package snastro.sintesi.applicazione.letture

import snastro.sintesi.applicazione.porte.MotivoDownload

/**
 * AC-S108: mirrors [snastro.sintesi.applicazione.porte.StatoModelloLinguistico] one-to-one — the
 * optional language model's availability as the Riassunto tab shows it.
 */
public sealed interface StatoModelloVista {
    public data class NonInstallato(val dimensioneByte: Long) : StatoModelloVista

    public data class InDownload(val scaricatiByte: Long, val totaliByte: Long) : StatoModelloVista

    public data class DownloadFallito(val motivo: MotivoDownload) : StatoModelloVista

    public data object Installato : StatoModelloVista
}
