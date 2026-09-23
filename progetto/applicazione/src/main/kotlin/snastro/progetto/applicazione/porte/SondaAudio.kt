package snastro.progetto.applicazione.porte

import snastro.kernel.Esito

/** Port: probes a source audio file before it is added to the Progetto (ADR 0005). */
public interface SondaAudio {
    /** [InfoAudio], or [ErroreAudioProgetto.AudioNonLeggibile] / [ErroreAudioProgetto.FormatoNonSupportato]. */
    public fun sonda(percorsoSorgente: String): Esito<InfoAudio>
}
