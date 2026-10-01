package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreRegistrazione] over Published Language data (passes [LettoreRegistrazioneContratto]), read live.
 * [parti] gives the Incontro's Registrazioni in the ITERATION order of [registrazioni], numbered 1..N: the order is
 * the supplier's ([INV-I2], Progetto's domain), so whoever plays the supplier keeps the map in that order — this fake
 * never computes it.
 */
public class LettoreRegistrazioneFinta(
    private val registrazioni: Map<RegistrazioneId, RegistrazioneVista> = emptyMap(),
) : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? = registrazioni[id]

    override fun parti(incontroId: IncontroId): List<ParteDiIncontroParlanti>? =
        registrazioni.values
            .filter { it.incontroId == incontroId }
            .mapIndexed { i, v -> ParteDiIncontroParlanti(v.registrazioneId, i + 1, v.dataRegistrazione) }
            .ifEmpty { null }
}
