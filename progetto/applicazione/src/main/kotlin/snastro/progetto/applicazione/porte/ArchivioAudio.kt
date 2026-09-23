package snastro.progetto.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio

/** Port: the project's `audio/` folder, where every source is copied (ADR 0010). */
public interface ArchivioAudio {
    /**
     * Copies the source into `audio/<id>.<source extension lowercased>` — `audio/<id>` when the source
     * name has no extension — and returns that reference, relative to the project folder. The copy is
     * verified (same size and content as the source) before [Esito.Ok]; otherwise
     * [ErroreApplicazioneProgetto.CopiaFallita], leaving no partial file.
     */
    public fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio>

    /**
     * Deletes the copied file [r]. It is the service's compensation when a command rolls back after a
     * successful [copia] — on the [Esito.Errore] path and on the exception path alike.
     * Idempotent: a missing file is a no-op. Confined to `audio/`: a reference that resolves outside
     * the project's `audio/` folder deletes nothing.
     */
    public fun scarta(r: RiferimentoAudio)
}
