package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreRegistrazione] over Published Language data (passes [LettoreRegistrazioneContratto]). The Parti of
 * an Incontro are its Registrazioni in [registrazioni]' iteration order, unless [ordine] gives that Incontro's order.
 */
public class LettoreRegistrazioneFinta(
    private val registrazioni: Map<RegistrazioneId, RegistrazioneVista> = emptyMap(),
    private val ordine: Map<IncontroId, List<RegistrazioneId>> = emptyMap(),
) : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? = registrazioni[id]

    override fun parti(incontroId: IncontroId): List<ParteDiIncontro>? =
        (ordine[incontroId] ?: registrazioni.values.filter { it.incontroId == incontroId }.map { it.registrazioneId })
            .ifEmpty { null }
            ?.mapIndexed { i, r -> ParteDiIncontro(r, i + 1) }
}
