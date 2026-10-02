package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.ParteSintesi

/**
 * [LettoreIncontro] over Progetto's public read API [CatalogoRegistrazioni.incontro] (boundary `porte-sintesi`,
 * ADR 0033 §4): the Parti come ordered and numbered by Progetto's `OrdineDelleParti` (INV-I2); this adapter maps them
 * field by field, never orders and never re-decides. `null` for an unknown or ceased Incontro.
 */
public class LettoreIncontroDaProgetto(private val catalogo: CatalogoRegistrazioni) : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<ParteSintesi>? =
        catalogo.incontro(incontroId)?.parti?.map { ParteSintesi(it.registrazioneId, it.numero) }
}
