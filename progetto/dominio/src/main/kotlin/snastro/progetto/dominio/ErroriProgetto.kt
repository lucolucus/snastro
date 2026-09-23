package snastro.progetto.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.RegistrazioneId

/** Expected failures of the Progetto context (ADR 0003, CR-8). */
public sealed interface ErroreProgetto : ErroreDominio {
    /** The Nome of a Progetto was empty or blank. */
    public data object NomeProgettoVuoto : ErroreProgetto

    /** The project database already holds a Progetto: only one is ever created in it. */
    public data object ProgettoGiaPresente : ErroreProgetto

    /** No Registrazione with that id exists (e.g. ModificaDataRegistrazione, AC-63). */
    public data class RegistrazioneNonTrovata(val id: RegistrazioneId) : ErroreProgetto
}
