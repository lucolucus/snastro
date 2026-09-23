package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.Registrazione

/**
 * Progetto slice of the S2 screen's data view (AC-161, R1 split — [RegistrazioneDelProgettoVista]
 * carries `registrazioneId, titolo, dataRegistrazione, durataMs`; `stati-elaborazione` and
 * `identificazione-registrazioni` carry the rest for `schermata-registrazioni` to combine).
 * Plain projection over [RegistrazioneRepository]: no domain rule, the [Registrazione] aggregate
 * stays the only owner of INV-1/INV-2.
 */
public class RegistrazioniDelProgetto(private val registrazioni: RegistrazioneRepository) {
    /**
     * The Registrazioni of [progettoId]: newest [Registrazione.dataRegistrazione] first, ties
     * broken by the most recently added ([Registrazione.aggiuntaAlle] desc); a final tie-break by
     * [Registrazione.id] keeps the order deterministic and stable across refreshes when both are equal.
     */
    public fun delProgetto(progettoId: ProgettoId): List<RegistrazioneDelProgettoVista> =
        registrazioni.delProgetto(progettoId)
            .sortedWith(
                compareByDescending<Registrazione> { it.dataRegistrazione }
                    .thenByDescending { it.aggiuntaAlle }
                    .thenBy { it.id.valore },
            )
            .map {
                RegistrazioneDelProgettoVista(
                    registrazioneId = it.id,
                    titolo = it.titolo,
                    dataRegistrazione = it.dataRegistrazione,
                    durataMs = it.durataMs,
                )
            }
}
