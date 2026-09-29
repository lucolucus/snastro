package snastro.avvio.progetto

import snastro.kernel.RegistrazioneId

/** Where the open project's content area is (S2, S3 of one Registrazione, Impostazioni — S5 is its Modelli section). */
internal sealed interface SchermataR1 {
    data object Registrazioni : SchermataR1

    data class Registrazione(val id: RegistrazioneId) : SchermataR1

    data object Impostazioni : SchermataR1
}
