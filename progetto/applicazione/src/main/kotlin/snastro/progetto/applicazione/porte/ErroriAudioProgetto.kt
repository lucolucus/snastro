// Named per dev-architecture-app.md#pacchetti (hierarchy file), not after its single declaration.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.progetto.applicazione.porte

import snastro.kernel.ErroreDominio

/**
 * Expected failures of the Progetto's audio ports ([SondaAudio], [ArchivioAudio]) — application-only
 * errors (ADR 0003): `ErroreProgetto` is sealed in `:progetto:dominio` and cannot be extended here.
 */
public sealed interface ErroreAudioProgetto : ErroreDominio {
    /** The source cannot be read as audio. */
    public data class AudioNonLeggibile(val percorsoSorgente: String) : ErroreAudioProgetto

    /** The source is readable but its format is not supported. */
    public data class FormatoNonSupportato(val percorsoSorgente: String) : ErroreAudioProgetto

    /** Copying the source into `audio/` failed; nothing was left behind. */
    public data class CopiaFallita(val percorsoSorgente: String) : ErroreAudioProgetto
}
