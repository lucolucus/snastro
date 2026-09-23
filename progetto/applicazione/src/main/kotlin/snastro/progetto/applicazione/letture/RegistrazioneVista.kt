package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Read view of a [snastro.progetto.dominio.Registrazione] (AC-97): the pinned Published Language
 * shape [CatalogoRegistrazioni] hands to the `registrazione-per-trascrizione`,
 * `registrazione-per-parlanti` and `trascritto-per-documento` boundaries — each consumer keeps its
 * own equal-shaped copy, this is the supplier's.
 */
public data class RegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
    val titolo: String,
    val riferimentoAudio: RiferimentoAudio,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
