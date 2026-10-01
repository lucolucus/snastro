package snastro.sbobinatura.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi

/**
 * In-memory [LettoreNomi] over Published Language data (passes [LettoreNomiContratto]):
 * [attribuzioni] maps each attributed Voce to its Parlante, [nomiParlanti] each Parlante (eliminato
 * included) to its current Nome. Both maps are read at every call, so a test may keep changing them;
 * an Attribuzione to a Parlante missing from [nomiParlanti] fails loudly.
 */
public class LettoreNomiFinta(
    private val attribuzioni: Map<VoceRef, ParlanteId> = emptyMap(),
    private val nomiParlanti: Map<ParlanteId, String> = emptyMap(),
) : LettoreNomi {
    // Every Incontro has its one Parte under the test convention (unIncontroDi / unicaParteDi, ADR 0033 §4.1).
    override fun nomi(id: RegistrazioneId): Map<VoceRef, String> =
        attribuzioni.filterKeys { it.incontroId == unIncontroDi(id) }.mapValues { (voce, p) ->
            checkNotNull(nomiParlanti[p]) { "Attribuzione di $voce a ${p.valore}, assente da nomiParlanti" }
        }

    override fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> =
        attribuzioni.filterValues { it == p }.keys.map { unicaParteDi(it) }.distinct()
}
