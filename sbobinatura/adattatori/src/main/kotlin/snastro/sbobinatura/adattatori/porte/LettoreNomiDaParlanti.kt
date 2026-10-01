package snastro.sbobinatura.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.NomiDelleVoci
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sbobinatura.applicazione.porte.LettoreNomi

/**
 * [LettoreNomi] over Parlanti's public read API [NomiDelleVoci] (boundary `porte-sbobinatura`, ADR 0002), keyed by
 * Incontro through Progetto's [CatalogoRegistrazioni]: [NomiDelleVoci] still answers per Registrazione with the
 * names of that Registrazione's whole Incontro, so any one Parte gives the Incontro's names, and the Parti it lists
 * map back to their Incontro. Delegates, never re-decides, never orders (ADR 0033 §4.1).
 */
public class LettoreNomiDaParlanti(
    private val parlanti: NomiDelleVoci,
    private val progetto: CatalogoRegistrazioni,
) : LettoreNomi {
    override fun nomi(incontroId: IncontroId): Map<VoceRef, String> =
        progetto.parti(incontroId)?.firstOrNull()?.let { parlanti.nomi(it) }.orEmpty()

    override fun incontriCon(p: ParlanteId): List<IncontroId> =
        parlanti.registrazioniCon(p).mapNotNull { progetto.registrazione(it)?.incontroId }.distinct()
}
