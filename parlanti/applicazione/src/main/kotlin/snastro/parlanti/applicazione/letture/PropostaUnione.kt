package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Attribuzione

/**
 * `proposta-unione`: [INV-22] two Voci of the SAME Incontro attributed to the same Parlante is
 * allowed, never blocked, never auto-merged — it yields a Proposta di unione (A, B) while the
 * condition holds. Read-only, recomputed live: a union or a re-attribution makes the pair
 * disappear on its own, this view never merges anything.
 */
public class PropostaUnione(
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
) {
    /**
     * [INV-22] per Incontro: every pair of Voci of the Incontro [id] — in the same or different Parti — attributed to
     * the same Parlante; never a pair across two Incontri (an Attribuzione is keyed by the Incontro's VoceRef).
     */
    public fun proposte(id: IncontroId): List<PropostaDiUnione> =
        attribuzioni.diIncontro(id)
            .groupBy { it.parlanteId }
            .flatMap { (parlanteId, attribuite) -> coppie(parlanteId, attribuite) }

    private fun coppie(parlanteId: ParlanteId, attribuite: List<Attribuzione>): List<PropostaDiUnione> {
        val nome = parlanti.trova(parlanteId)?.nome?.valore
        val voci = attribuite.map { it.voceRef.voceId }.sortedBy { it.numero }
        return if (nome == null || voci.size < 2) {
            emptyList()
        } else {
            voci.indices.flatMap { i ->
                (i + 1 until voci.size).map { j -> PropostaDiUnione(voci[i], voci[j], parlanteId, nome) }
            }
        }
    }
}

/** One row of [PropostaUnione.proposte]: [INV-22], `voceA` always before `voceB`. */
public data class PropostaDiUnione(
    val voceA: VoceId,
    val voceB: VoceId,
    val parlanteId: ParlanteId,
    val nome: String,
)
