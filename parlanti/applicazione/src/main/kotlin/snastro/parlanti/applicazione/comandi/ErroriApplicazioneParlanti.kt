package snastro.parlanti.applicazione.comandi

import snastro.kernel.ErroreDominio
import snastro.kernel.ParlanteId

/**
 * The application/technical failures of the Parlanti context raised in `..applicazione.comandi..` —
 * ONE hierarchy per module (ADR 0003): domain rules stay in `ErroreParlanti` (`:parlanti:dominio`).
 */
public sealed interface ErroreApplicazioneParlanti : ErroreDominio {
    /** The command's `parlanteId` matches no Parlante of the Progetto (active or `eliminato`). */
    public data class ParlanteNonTrovato(val id: ParlanteId) : ErroreApplicazioneParlanti
}
