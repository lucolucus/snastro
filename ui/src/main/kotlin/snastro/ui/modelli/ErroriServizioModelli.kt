package snastro.ui.modelli

import snastro.kernel.ErroreDominio

/**
 * `tec-modelli-ui` (owned here): 1:1 image of `snastro.modelli.ErroreModelli`, mapped in `:avvio`
 * (block `avvio-composizione`, AC-329) — declared on the UI side because `:ui` must not depend on
 * `:modelli` (CR-1). Same field name `modelloId` on both sides.
 */
sealed interface ErroreServizioModelli : ErroreDominio {
    /** The downloaded asset's bytes don't match the catalogue entry's hash; nothing was installed. */
    data class HashNonValido(val modelloId: String) : ErroreServizioModelli

    /** An archive's entry is unsafe (escapes the destination, or is a symlink/hard link). */
    data class ArchivioNonValido(val modelloId: String) : ErroreServizioModelli

    /** No connection could be made at all (unreachable host, connection refused/timed out). */
    data object ReteAssente : ErroreServizioModelli

    /** A local filesystem operation failed (disk full, cache folder not writable, rename failed). */
    data class ScritturaFallita(val motivo: String) : ErroreServizioModelli

    /** The source was reachable but the download failed (bad status, redirect loop, a stalled/short transfer). */
    data class DownloadFallito(val motivo: String) : ErroreServizioModelli
}
