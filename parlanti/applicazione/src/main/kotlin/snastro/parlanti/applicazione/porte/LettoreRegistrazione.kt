package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Parlanti's own consumer-owned port through which it reads a Registrazione from Progetto
 * (boundary `registrazione-per-parlanti`, Customer/Supplier, read-only, Published Language).
 */
public interface LettoreRegistrazione {
    /** The Registrazione with [id] as it is NOW in the catalogue, or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista?

    /**
     * The Parti (Registrazioni) of the Incontro [incontroId], UNORDERED: no caller sorts this list nor relies on its
     * order (ADR 0033 §4.1, D-0031; ordered and numbered in wave 4). `null` for an unknown Incontro or one that ceased
     * with its last Parte; a known Incontro has at least one Parte.
     */
    public fun parti(incontroId: IncontroId): List<RegistrazioneId>?
}
