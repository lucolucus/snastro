package snastro.progetto.applicazione.porte

import snastro.kernel.Esito

/** Port: probes a source audio file before it is added to the Progetto (ADR 0005). */
public interface SondaAudio {
    /**
     * [Esito.Ok] only for a playable source, and then always with [InfoAudio.durataMs] > 0.
     * A missing, empty (zero-length) or unreadable file, or a directory →
     * [ErroreApplicazioneProgetto.AudioNonLeggibile]; a readable file of an unsupported format →
     * [ErroreApplicazioneProgetto.FormatoNonSupportato].
     */
    public fun sonda(percorsoSorgente: String): Esito<InfoAudio>
}
