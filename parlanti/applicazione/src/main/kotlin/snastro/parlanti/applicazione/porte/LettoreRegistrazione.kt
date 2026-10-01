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
     * The Parti of the Incontro [incontroId] in its order ([INV-I2], Progetto's), numbered 1..N, each with its current
     * dataRegistrazione (ADR 0033 §4, widened from the unordered read of §4.1). `null` for an unknown Incontro or one
     * that ceased with its last Parte; a known Incontro has at least one Parte.
     */
    public fun parti(incontroId: IncontroId): List<ParteDiIncontroParlanti>?
}
