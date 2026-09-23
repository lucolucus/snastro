package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.Registrazione

/** Repository port of the [Registrazione] aggregate. */
public interface RegistrazioneRepository {
    public fun trova(id: RegistrazioneId): Registrazione?

    /** Every Registrazione of the Progetto [id], in no guaranteed order. */
    public fun delProgetto(id: ProgettoId): List<Registrazione>

    /**
     * The titolo of every Registrazione of the Progetto [id], in no guaranteed order (empty if none)
     * — titles only, for the titolo uniqueness of `AggiungiRegistrazione` (AC-322).
     */
    public fun titoliDelProgetto(id: ProgettoId): List<String>

    /** Inserts or updates [r] inside the caller's transaction. */
    public fun salva(r: Registrazione)
}
