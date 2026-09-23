package snastro.modelli

import snastro.kernel.ErroreDominio

/**
 * Expected failures of model provisioning (ADR 0008 Amendment (c)). `:modelli` is a technical
 * module, not a bounded context, but it returns `Esito` like one and so owns its own sealed
 * hierarchy (ADR 0003 amended).
 */
public sealed interface ErroreModelli : ErroreDominio {
    /** The downloaded asset's bytes don't match [VoceCatalogo.sha256]; nothing was installed. */
    public data class HashNonValido(public val modelloId: String) : ErroreModelli

    /** An archive's entry is unsafe (escapes the destination, or is a symlink/hard link). */
    public data class ArchivioNonValido(public val modelloId: String, public val motivo: String) : ErroreModelli

    /** No connection could be made at all (unreachable host, connection refused/timed out). */
    public data object ReteAssente : ErroreModelli

    /** A local filesystem operation failed (disk full, cache folder not writable, rename failed). */
    public data class ScritturaFallita(public val motivo: String) : ErroreModelli

    /** The source was reachable but the download failed (bad status, redirect loop, a stalled/short transfer). */
    public data class DownloadFallito(public val motivo: String) : ErroreModelli
}
