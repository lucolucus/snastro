package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.sintesi.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `porte-sintesi`, ADR 0033 §4): [nomi]
 * already answers per Incontro with exactly the pinned Published Language shape — delegates, never re-decides,
 * never orders. `ParlanteId`/`snastro.parlanti.*` never cross: only the `Map<VoceRef, String>` it returns (INV-S5).
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
) : LettoreNomi {
    override fun nomi(incontroId: IncontroId): Map<VoceRef, String> = parlanti.nomi(incontroId)
}
