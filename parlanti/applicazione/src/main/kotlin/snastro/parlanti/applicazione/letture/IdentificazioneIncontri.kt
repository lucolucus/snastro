package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreVoci

/**
 * Parlanti slice of the S2 Incontro badge (`viste-parlanti-incontro`, AC-I47): per Incontro, how many Voci it has
 * across all its Parti and how many still need identification. Read-only: no rule lives here (RC-1) —
 * [LettoreVoci] decides whether a Trascritto exists (INV-5), [AttribuzioneRepository] which Voci are attributed.
 */
public class IdentificazioneIncontri(
    private val voci: LettoreVoci,
    private val attribuzioni: AttribuzioneRepository,
) {
    /** AC-I47: one entry per Incontro of [incontroIds] with at least one transcribed Parte. */
    public fun conteggi(incontroIds: List<IncontroId>): Map<IncontroId, IdentificazioneIncontro> =
        incontroIds.mapNotNull { id ->
            voci.voci(id)?.let { vociDellIncontro ->
                val attribuite = attribuzioni.diIncontro(id).map { it.voceRef }.toSet()
                id to IdentificazioneIncontro(
                    numVoci = vociDellIncontro.size,
                    numVociDaIdentificare = vociDellIncontro.count { it.voceRef !in attribuite },
                )
            }
        }.toMap()
}

/** One value of [IdentificazioneIncontri.conteggi]: AC-I47. */
public data class IdentificazioneIncontro(
    val numVoci: Int,
    val numVociDaIdentificare: Int,
)
