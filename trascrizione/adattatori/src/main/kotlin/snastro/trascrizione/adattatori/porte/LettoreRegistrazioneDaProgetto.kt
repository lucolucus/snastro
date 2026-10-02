package snastro.trascrizione.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.RegistrazioneVista

/**
 * [LettoreRegistrazione] over Progetto's public read API [CatalogoRegistrazioni] (boundary
 * `registrazione-per-trascrizione`, ADR 0002): calls the supplier's `registrazione(id)` and maps its
 * answer field by field into this context's own [RegistrazioneVista] — delegates, never re-decides.
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

    /** The Parti of [incontroId] as Progetto orders and numbers them (INV-I2, `CatalogoRegistrazioni.incontro`). */
    override fun parti(incontroId: IncontroId): List<ParteDiIncontro>? =
        catalogo.incontro(incontroId)?.parti?.map { ParteDiIncontro(it.registrazioneId, it.numero) }
}
