package snastro.sintesi.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sintesi.applicazione.porte.LettoreIncontro

/**
 * [LettoreIncontro] over Progetto's public read API [CatalogoRegistrazioni] (boundary `lettore-incontro-sintesi`,
 * ADR 0033 §4.1): delegates to the supplier's `parti`, never re-decides (no order, no filter).
 */
public class LettoreIncontroDaProgetto(private val catalogo: CatalogoRegistrazioni) : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<RegistrazioneId>? = catalogo.parti(incontroId)
}
