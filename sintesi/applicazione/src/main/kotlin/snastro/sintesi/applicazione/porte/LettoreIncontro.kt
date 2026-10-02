package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * Consumer-owned, read-only port through which Sintesi reads the Parti of an Incontro from Progetto (boundary
 * `porte-sintesi`, ADR 0033 §4; Published Language only). Contract: `LettoreIncontroContratto`.
 */
public interface LettoreIncontro {
    /**
     * The Parti of the Incontro [incontroId] in INV-I2 order, numbered 1..N. The order is Progetto's (its domain
     * `OrdineDelleParti`): no Sintesi code sorts these Parti. `null` when the Incontro is unknown or ceased with its
     * last Parte; a known Incontro has ≥ 1 Parte.
     */
    public fun parti(incontroId: IncontroId): List<ParteSintesi>?
}

/** One Parte of an Incontro as Sintesi reads it: the Registrazione and its [numero] della parte (1-based). */
public data class ParteSintesi(val registrazioneId: RegistrazioneId, val numero: Int) {
    init {
        require(numero >= 1) { "numero della parte da 1: $numero" }
    }
}
