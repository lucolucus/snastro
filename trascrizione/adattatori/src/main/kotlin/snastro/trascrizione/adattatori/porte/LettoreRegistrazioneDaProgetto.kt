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

    /**
     * TRANSITION (D-0037, until `adattatori-trascrizione-incontro` reads Progetto's ordered Parti): numbered in the
     * order of Progetto's UNORDERED `parti` — exact while every Incontro has one Parte (no import gives it a second one
     * before I2), which is why the multi-Parte contract cases stay off for this adapter.
     */
    override fun parti(incontroId: IncontroId): List<ParteDiIncontro>? =
        catalogo.parti(incontroId)?.mapIndexed { i, r -> ParteDiIncontro(r, i + 1) }
}
