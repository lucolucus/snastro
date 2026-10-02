package snastro.sbobinatura.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.sbobinatura.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `porte-sbobinatura`, ADR 0002): both reads
 * are keyed by Incontro on both sides (ADR 0033 §4), so it only delegates, never re-decides, never orders.
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
) : LettoreNomi {
    override fun nomi(incontroId: IncontroId): Map<VoceRef, String> = parlanti.nomi(incontroId)

    override fun incontriCon(p: ParlanteId): List<IncontroId> = parlanti.incontriCon(p)
}
