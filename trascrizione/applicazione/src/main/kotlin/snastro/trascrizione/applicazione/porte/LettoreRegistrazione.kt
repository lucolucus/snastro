package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned port through which Trascrizione reads a Registrazione from Progetto
 * (boundary `registrazione-per-trascrizione`, Customer/Supplier, read-only, Published Language).
 */
public interface LettoreRegistrazione {
    /** The Registrazione with [id], or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista?
}
