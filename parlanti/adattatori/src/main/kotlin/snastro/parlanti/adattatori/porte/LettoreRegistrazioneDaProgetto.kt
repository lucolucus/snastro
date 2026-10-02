package snastro.parlanti.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.ParteDiIncontroParlanti
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni

/**
 * [LettoreRegistrazione] over Progetto's public read API [CatalogoRegistrazioni] (boundary
 * `registrazione-per-parlanti`, ADR 0002): calls the supplier's `registrazione(id)` and maps its
 * answer field by field into this context's own [RegistrazioneVista] — delegates, never re-decides.
 *
 * [parti] maps Progetto's `incontro(id)` (Parti already ordered and numbered by `OrdineDelleParti`, ADR 0033 §7): the
 * order is decided only in Progetto's domain, copied here as-is, never re-sorted.
 */
public class LettoreRegistrazioneDaProgetto(
    private val catalogo: CatalogoRegistrazioni,
) : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista? =
        catalogo.registrazione(id)?.let { vista ->
            RegistrazioneVista(
                registrazioneId = vista.registrazioneId,
                progettoId = vista.progettoId,
                incontroId = vista.incontroId,
                titolo = vista.titolo,
                riferimentoAudio = vista.riferimentoAudio,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
            )
        }

    override fun parti(incontroId: IncontroId): List<ParteDiIncontroParlanti>? =
        catalogo.incontro(incontroId)?.parti?.map {
            ParteDiIncontroParlanti(it.registrazioneId, it.numero, it.dataRegistrazione)
        }
}
