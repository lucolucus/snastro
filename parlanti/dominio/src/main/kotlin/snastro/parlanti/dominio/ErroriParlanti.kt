// Named per dev-architecture-app.md#pacchetti (context error hierarchy file), not after its single declaration.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.parlanti.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.ParlanteId

/** Expected rule violations of the Parlanti context (ADR 0003, CR-8). Later blocks add their members here. */
public sealed interface ErroreParlanti : ErroreDominio {
    /** [INV-13] an `eliminato` Parlante is terminal. */
    public data class ParlanteEliminatoNonModificabile(val id: ParlanteId) : ErroreParlanti

    /** [INV-18] promozione is only `occasionale` → `ricorrente`. */
    public data class PromozioneNonAmmessa(val id: ParlanteId) : ErroreParlanti

    /** AC-22 a Nome is never empty. */
    public data object NomeVuoto : ErroreParlanti
}
