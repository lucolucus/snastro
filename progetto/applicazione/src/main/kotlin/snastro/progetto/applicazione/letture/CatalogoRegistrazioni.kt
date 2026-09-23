package snastro.progetto.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.porte.RegistrazioneRepository

/**
 * The Progetto context's public read API (AC-97): a plain projection over [RegistrazioneRepository],
 * no domain rule (the [snastro.progetto.dominio.Registrazione] aggregate stays the only owner of
 * INV-1/INV-2). Other contexts' adapters (`registrazione-da-progetto-tr`, `-pa`, `-doc`, future
 * blocks) call it directly — the allowed `consumer:adattatori -> supplier:applicazione` edge (CR-1).
 */
public class CatalogoRegistrazioni(private val registrazioni: RegistrazioneRepository) {
    /** The Registrazione [id] as a [RegistrazioneVista], or `null` if the catalogue does not know it. */
    public fun registrazione(id: RegistrazioneId): RegistrazioneVista? =
        registrazioni.trova(id)?.let { r ->
            RegistrazioneVista(
                registrazioneId = r.id,
                progettoId = r.progettoId,
                titolo = r.titolo,
                riferimentoAudio = r.riferimentoAudio,
                dataRegistrazione = r.dataRegistrazione,
                durataMs = r.durataMs,
            )
        }
}
