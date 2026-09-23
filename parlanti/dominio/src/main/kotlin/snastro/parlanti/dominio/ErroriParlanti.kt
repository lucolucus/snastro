package snastro.parlanti.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.ParlanteId

/** Expected rule violations of the Parlanti context (ADR 0003, CR-8). Later blocks add their members here. */
public sealed interface ErroreParlanti : ErroreDominio {
    /** The command's `parlanteId` matches no Parlante of the Progetto (active or `eliminato`). */
    public data class ParlanteNonTrovato(val id: ParlanteId) : ErroreParlanti

    /** [INV-13] an `eliminato` Parlante is terminal. */
    public data class ParlanteEliminatoNonModificabile(val id: ParlanteId) : ErroreParlanti

    /** [INV-18] promozione is only `occasionale` → `ricorrente`. */
    public data class PromozioneNonAmmessa(val id: ParlanteId) : ErroreParlanti

    /** [INV-16] the normalized [nome] is already used by an `attivo` Parlante of the same Progetto (ADR 0007). */
    public data class NomeGiaInUso(val nome: String) : ErroreParlanti

    /** AC-22 a Nome is never empty. */
    public data object NomeVuoto : ErroreParlanti
}
