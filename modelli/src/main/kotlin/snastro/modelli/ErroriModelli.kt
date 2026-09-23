package snastro.modelli

import snastro.kernel.ErroreDominio

/**
 * Expected failures of model provisioning (ADR 0008). `:modelli` is a technical module, not a
 * bounded context, but it returns `Esito` like one and so owns its own sealed hierarchy (ADR 0003
 * amended).
 */
public sealed interface ErroreModelli : ErroreDominio {
    /** The downloaded bytes don't match [VoceCatalogo.sha256]; nothing was installed. */
    public data class HashNonValido(public val id: String) : ErroreModelli

    /** No connection could be made, or it dropped before every declared byte arrived. */
    public data object ReteAssente : ErroreModelli

    /** The source answered but refused the download (e.g. an unexpected HTTP status). */
    public data class DownloadFallito(public val motivo: String) : ErroreModelli
}
