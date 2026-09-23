package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * Parlanti's own consumer-owned port through which it reads a Registrazione from Progetto
 * (boundary `registrazione-per-parlanti`, Customer/Supplier, read-only, Published Language).
 */
public interface LettoreRegistrazione {
    /** The Registrazione with [id] as it is NOW in the catalogue, or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista?
}
