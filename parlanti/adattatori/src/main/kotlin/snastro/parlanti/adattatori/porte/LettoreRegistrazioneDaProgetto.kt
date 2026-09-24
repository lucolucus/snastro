package snastro.parlanti.adattatori.porte

import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.RegistrazioneVista
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni

/**
 * [LettoreRegistrazione] over Progetto's public read API [CatalogoRegistrazioni] (boundary
 * `registrazione-per-parlanti`, ADR 0002): calls the supplier's `registrazione(id)` and maps its
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
                titolo = vista.titolo,
                riferimentoAudio = vista.riferimentoAudio,
                dataRegistrazione = vista.dataRegistrazione,
                durataMs = vista.durataMs,
            )
        }
}
