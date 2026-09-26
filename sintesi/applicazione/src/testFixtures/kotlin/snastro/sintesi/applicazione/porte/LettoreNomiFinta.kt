package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * In-memory [LettoreNomi] over Published Language data (passes [LettoreNomiContratto]):
 * [attribuzioni] maps each attributed Voce to an opaque Parlante key, [nomiParlanti] each key
 * (eliminato included) to its current Nome. Both maps are read at every call, so a test may keep
 * changing them; an Attribuzione to a key missing from [nomiParlanti] fails loudly.
 */
public class LettoreNomiFinta(
    private val attribuzioni: Map<VoceRef, String> = emptyMap(),
    private val nomiParlanti: Map<String, String> = emptyMap(),
) : LettoreNomi {
    override fun nomi(r: RegistrazioneId): Map<VoceRef, String> =
        attribuzioni.filterKeys { it.registrazioneId == r }.mapValues { (voce, p) ->
            checkNotNull(nomiParlanti[p]) { "Attribuzione di $voce a $p, assente da nomiParlanti" }
        }
}
