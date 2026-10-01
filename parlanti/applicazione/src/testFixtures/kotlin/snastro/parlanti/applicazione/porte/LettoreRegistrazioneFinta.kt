package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/** In-memory [LettoreRegistrazione] over Published Language data (passes [LettoreRegistrazioneContratto]). */
public class LettoreRegistrazioneFinta(
    private val registrazioni: Map<RegistrazioneId, RegistrazioneVista> = emptyMap(),
) : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? = registrazioni[id]

    override fun parti(incontroId: IncontroId): List<RegistrazioneId>? =
        registrazioni.values.filter { it.incontroId == incontroId }.map { it.registrazioneId }.ifEmpty { null }
}
