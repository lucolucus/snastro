// dev-architecture-app.md#pacchetti pins the file name `Errori<Contesto>.kt` for a context's error hierarchy.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.applicazione.porte

import snastro.kernel.ErroreDominio
import snastro.kernel.RegistrazioneId

/**
 * Expected failures raised by the application layer of Trascrizione (ports and services of
 * `:trascrizione:applicazione`, ADR 0003 as amended: one hierarchy per context per module). The
 * domain-rule errors of the aggregates stay in `ErroreTrascrizione` (`:trascrizione:dominio`).
 */
public sealed interface ErroreApplicazioneTrascrizione : ErroreDominio {
    /** INV-4 (ADR 0007, `elaborazione_aperta_unica`): the Registrazione already has an open Elaborazione. */
    public data class ElaborazioneGiaAperta(val registrazioneId: RegistrazioneId) : ErroreApplicazioneTrascrizione

    /** INV-4 (ADR 0007, `elaborazione_completata_unica`): the Registrazione already has a `completata` one. */
    public data class ElaborazioneGiaCompletata(val registrazioneId: RegistrazioneId) : ErroreApplicazioneTrascrizione
}
