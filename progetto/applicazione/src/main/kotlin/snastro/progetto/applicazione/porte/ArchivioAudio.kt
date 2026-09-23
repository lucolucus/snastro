package snastro.progetto.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio

/** Port: the project's `audio/` folder, where every source is copied (ADR 0010). */
public interface ArchivioAudio {
    /**
     * Copies the source into `audio/<id>.<source extension lowercased>` and returns that reference,
     * relative to the project folder; or [ErroreAudioProgetto.CopiaFallita], leaving no partial file.
     */
    public fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>

    /** Deletes the copied file [r] (e.g. when the command that copied it is rolled back). */
    public fun scarta(r: RiferimentoAudio)
}
