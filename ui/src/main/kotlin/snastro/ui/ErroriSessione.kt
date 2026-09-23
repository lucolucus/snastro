package snastro.ui

import snastro.kernel.ErroreDominio

/**
 * `tec-shell-ui` (owned here): expected failures of [SessioneProgetto]'s `crea`/`apri` (ADR 0003,
 * R25). No `CartellaGiaEsistente` — `crea` derives a free folder name instead of failing (AC-264,
 * re-pinned 2026-09-23, user decision).
 */
sealed interface ErroreSessione : ErroreDominio {
    /** The `nome` given to `crea` is blank. */
    data object NomeProgettoVuoto : ErroreSessione

    /** The `cartellaGenitore`/`percorso` given to `crea`/`apri` does not name a usable project folder. */
    data object CartellaNonValida : ErroreSessione

    /** The project's `.lock` is held by another running instance (ADR 0010). */
    data object ProgettoGiaAperto : ErroreSessione

    /** The project's schema/data was written by a newer version of the app. */
    data object DatabasePiuRecente : ErroreSessione
}
