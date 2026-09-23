package snastro.progetto.applicazione.letture

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * Progetto slice of the `schermata-registrazioni` screen's data view (AC-161, R1 split): carries
 * `registrazioneId, titolo, dataRegistrazione, durataMs` — `stati-elaborazione` and
 * `identificazione-registrazioni` carry the rest, the screen combines them. Own type (never the
 * cross-context [snastro.progetto.applicazione.letture.RegistrazioneVista] published by
 * `catalogo-registrazioni`): this block's shape is pinned to exactly these 4 fields.
 */
public data class RegistrazioneDelProgettoVista(
    val registrazioneId: RegistrazioneId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
