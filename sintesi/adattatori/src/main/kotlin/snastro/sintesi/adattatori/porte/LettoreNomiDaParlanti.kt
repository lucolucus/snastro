package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sintesi.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `porte-sintesi`, ADR 0033 §4), keyed by
 * Incontro through Progetto's [CatalogoRegistrazioni]: [NomiDelleVoci.nomi] still answers per Registrazione with the
 * names of that Registrazione's whole Incontro, so any one Parte gives the Incontro's names. Delegates, never
 * re-decides, never orders: only the `Map<VoceRef, String>` Parlanti returns crosses (INV-S5).
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
    private val progetto: CatalogoRegistrazioni,
) : LettoreNomi {
    override fun nomi(incontroId: IncontroId): Map<VoceRef, String> =
        progetto.parti(incontroId)?.firstOrNull()?.let(parlanti::nomi).orEmpty()
}
