package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.ParteSintesi

/**
 * [LettoreIncontro] over Progetto's public read API [CatalogoRegistrazioni] (boundary `porte-sintesi`, ADR 0033 §4):
 * delegates, never re-decides and never orders. TRANSITION (ADR 0033 §4.1, §6, D-0037): [CatalogoRegistrazioni.parti]
 * is unordered until `catalogo-incontro` gives the Parti ordered and numbered by Progetto's `OrdineDelleParti`, so this
 * adapter answers for a one-Parte Incontro only (its Parte 1) and fails closed on more.
 */
public class LettoreIncontroDaProgetto(private val catalogo: CatalogoRegistrazioni) : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<ParteSintesi>? =
        catalogo.parti(incontroId)?.let { parti ->
            check(parti.size == 1) {
                "Incontro $incontroId con ${parti.size} Parti: l'ordine delle Parti e' di catalogo-incontro"
            }
            listOf(ParteSintesi(parti.single(), 1))
        }
}
