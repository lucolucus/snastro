package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi

/**
 * In-memory [LettoreIncontro] over Published Language data (passes [LettoreIncontroContratto]), read live: a test may
 * pass a mutable map and change it. Each list is the Incontro's Parti in the supplier's order (INV-I2, Progetto's
 * domain), numbered 1..N by position: whoever plays the supplier keeps the list in that order, this fake never
 * computes it.
 */
public class LettoreIncontroFinta(
    private val parti: Map<IncontroId, List<RegistrazioneId>> = emptyMap(),
) : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<ParteSintesi>? =
        parti[incontroId]?.mapIndexed { i, r -> ParteSintesi(r, i + 1) }?.ifEmpty { null }
}

/**
 * A [LettoreIncontro] where every Incontro of the test convention ([unIncontroDi]) is its one-Parte one
 * ([unicaParteDi]): for tests about something else. Any other id is unknown (`null`), like the supplier's.
 */
public fun ogniIncontroConUnaParte(): LettoreIncontro = object : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<ParteSintesi>? =
        unicaParteDi(incontroId).takeIf { unIncontroDi(it) == incontroId }?.let { listOf(ParteSintesi(it, 1)) }
}
