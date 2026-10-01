package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.OrdineDelleRegistrazioni
import snastro.progetto.dominio.Registrazione

/**
 * Progetto slice of the S2 screen's data view (AC-161, R1 split — [RegistrazioneDelProgettoVista]
 * carries `registrazioneId, titolo, dataRegistrazione, durataMs`; `stati-elaborazione` and
 * `identificazione-registrazioni` carry the rest for `schermata-registrazioni` to combine).
 * Plain projection over [RegistrazioneRepository]: no domain rule, the [Registrazione] aggregate
 * stays the only owner of INV-1/INV-2.
 */
public class RegistrazioniDelProgetto(private val registrazioni: RegistrazioneRepository) {
    /** The Registrazioni of [progettoId] in the S2 order decided by [OrdineDelleRegistrazioni]. */
    public fun delProgetto(progettoId: ProgettoId): List<RegistrazioneDelProgettoVista> =
        OrdineDelleRegistrazioni.ordina(registrazioni.delProgetto(progettoId))
            .map {
                RegistrazioneDelProgettoVista(
                    registrazioneId = it.id,
                    titolo = it.titolo,
                    dataRegistrazione = it.dataRegistrazione,
                    durataMs = it.durataMs,
                )
            }
}
