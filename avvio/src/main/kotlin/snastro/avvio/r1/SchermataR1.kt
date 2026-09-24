package snastro.avvio.r1

import snastro.kernel.RegistrazioneId

/** Where the open project's content area is (S2, S3 of one Registrazione, S5). */
internal sealed interface SchermataR1 {
    data object Registrazioni : SchermataR1

    data class Registrazione(val id: RegistrazioneId) : SchermataR1

    data object Modelli : SchermataR1
}
