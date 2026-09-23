// Named per dev-architecture-app.md#pacchetti (hierarchy / events file), not after its single declaration.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.progetto.dominio

import snastro.kernel.ErroreDominio

/** Expected failures of the Progetto context (ADR 0003, CR-8). */
public sealed interface ErroreProgetto : ErroreDominio {
    /** The Nome of a Progetto was empty or blank. */
    public data object NomeProgettoVuoto : ErroreProgetto
}
