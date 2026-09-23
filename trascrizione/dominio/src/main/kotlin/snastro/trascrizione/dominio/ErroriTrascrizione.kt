// dev-architecture-app.md#pacchetti pins the file name `Errori<Contesto>.kt` for a context's error hierarchy.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.dominio

import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDominio

/** Expected rule violations of the Trascrizione context (ADR 0003). */
public sealed interface ErroreTrascrizione : ErroreDominio {
    /** INV-3: [Elaborazione] cannot move from [da] to [verso]; its state is unchanged. */
    public data class TransizioneNonAmmessa(
        val id: ElaborazioneId,
        val da: StatoElaborazione,
        val verso: StatoElaborazione,
    ) : ErroreTrascrizione
}
