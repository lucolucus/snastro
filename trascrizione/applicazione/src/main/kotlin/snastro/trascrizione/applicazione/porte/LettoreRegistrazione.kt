package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned port through which Trascrizione reads a Registrazione from Progetto
 * (boundary `registrazione-per-trascrizione`, Customer/Supplier, read-only, Published Language).
 */
public interface LettoreRegistrazione {
    /** The Registrazione with [id], or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista?

    /**
     * The Parti of the Incontro [incontroId] in the Incontro's order (INV-I2, decided by Progetto), numbered 1..N as
     * the supplier gives them; `null` for an unknown Incontro (boundary `parti-per-trascrizione`, ADR 0033 §4).
     */
    public fun parti(incontroId: IncontroId): List<ParteDiIncontro>?
}
