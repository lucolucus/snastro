package snastro.parlanti.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/** Expected rule violations of the Parlanti context (ADR 0003, CR-8). Later blocks add their members here. */
public sealed interface ErroreParlanti : ErroreDominio {
    /**
     * The command's `parlanteId` matches no Parlante of the Progetto (active or `eliminato`).
     * [INV-17] (conferma-attribuzione) also folds a Parlante of another Progetto into this case.
     */
    public data class ParlanteNonTrovato(val id: ParlanteId) : ErroreParlanti

    /** [INV-5] the Registrazione behind a `voceRef` has no Trascritto: unknown id, or no Elaborazione completata. */
    public data class TrascrittoNonTrovato(val registrazioneId: RegistrazioneId) : ErroreParlanti

    /** The Trascritto exists but none of its current Voci matches the given `voceRef`. */
    public data class VoceNonTrovata(val voceRef: VoceRef) : ErroreParlanti

    /** [INV-13] an `eliminato` Parlante is terminal. */
    public data class ParlanteEliminatoNonModificabile(val id: ParlanteId) : ErroreParlanti

    /** [INV-18] promozione is only `occasionale` → `ricorrente`. */
    public data class PromozioneNonAmmessa(val id: ParlanteId) : ErroreParlanti

    /** [INV-16] the normalized [nome] is already used by an `attivo` Parlante of the same Progetto (ADR 0007). */
    public data class NomeGiaInUso(val nome: String) : ErroreParlanti

    /** AC-22 a Nome is never empty. */
    public data object NomeVuoto : ErroreParlanti

    /** [INV-19]/AC-89 SaltaVoce refuses an already-attributed Voce; nothing changes. */
    public data class VoceGiaAttribuita(val voceRef: VoceRef) : ErroreParlanti

    /**
     * ADR 0012 Amendment (b) point 2: the Voce's [SorgenteImpronta] changed between the print extraction
     * and the command's transaction; nothing written, the user retries.
     */
    public data class VoceCambiata(val voceRef: VoceRef) : ErroreParlanti
}
