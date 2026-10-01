package snastro.progetto.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate

/**
 * Read view of a [snastro.progetto.dominio.Registrazione] (AC-97): the pinned Published Language
 * shape [CatalogoRegistrazioni] hands to the `registrazione-per-trascrizione`,
 * `registrazione-per-parlanti` and `trascritto-per-sbobinatura` boundaries — each consumer keeps its
 * own equal-shaped copy, this is the supplier's. [incontroId] is the Incontro the Registrazione is a Parte of (ADR 0033
 * §4.1, final shape).
 */
public data class RegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val progettoId: ProgettoId,
    val incontroId: IncontroId,
    val titolo: String,
    val riferimentoAudio: RiferimentoAudio,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
)
