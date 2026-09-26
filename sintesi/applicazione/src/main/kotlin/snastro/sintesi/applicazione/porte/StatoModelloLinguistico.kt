package snastro.sintesi.applicazione.porte

/**
 * The optional language model's availability as Sintesi sees it. The INV-S6 guard needs only
 * [Installato]; `riassunto-vista` needs all four.
 */
public sealed interface StatoModelloLinguistico {
    /** Not on disk (or its marker does not match the catalogue); downloading it takes [dimensioneByte] (> 0). */
    public data class NonInstallato(val dimensioneByte: Long) : StatoModelloLinguistico

    /** A download is running in this session: `0 <= scaricatiByte <= totaliByte`, [scaricatiByte] never decreasing. */
    public data class InDownload(val scaricatiByte: Long, val totaliByte: Long) : StatoModelloLinguistico

    /** The last download of this session failed for [motivo]; nothing was installed. */
    public data class DownloadFallito(val motivo: MotivoDownload) : StatoModelloLinguistico

    /** Installed: the installed marker matches the catalogue's SHA-256. */
    public data object Installato : StatoModelloLinguistico
}
