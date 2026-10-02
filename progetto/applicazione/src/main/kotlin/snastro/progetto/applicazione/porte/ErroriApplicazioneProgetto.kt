package snastro.progetto.applicazione.porte

import snastro.kernel.ErroreDominio
import snastro.kernel.IncontroId

/**
 * The application/technical failures of the Progetto context — ONE hierarchy per module (ADR 0003,
 * user decision 2026-09-23): domain rules stay in `ErroreProgetto` (`:progetto:dominio`).
 */
public sealed interface ErroreApplicazioneProgetto : ErroreDominio {
    /** The source cannot be read as audio (missing, empty, a directory, corrupted). */
    public data class AudioNonLeggibile(val percorsoSorgente: String) : ErroreApplicazioneProgetto

    /** The source is readable but its format is not supported. */
    public data class FormatoNonSupportato(val percorsoSorgente: String) : ErroreApplicazioneProgetto

    /** Copying the source into `audio/` failed; nothing was left behind. */
    public data class CopiaFallita(val percorsoSorgente: String) : ErroreApplicazioneProgetto

    /** INV-I1: the destination Incontro of an import is unknown, of another Progetto, or ceased with its last Parte. */
    public data class IncontroNonTrovato(val id: IncontroId) : ErroreApplicazioneProgetto
}
