package snastro.progetto.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import java.time.LocalTime

/** Read view of an Incontro (ADR 0033 §1): its [parti] are ordered and numbered by `OrdineDelleParti`. */
public data class IncontroVista(
    val incontroId: IncontroId,
    val progettoId: ProgettoId,
    val parti: List<ParteVista>,
)

/** A Parte of an Incontro; [numero] (1..N) is minted at read time, never stored. */
public data class ParteVista(
    val registrazioneId: RegistrazioneId,
    val numero: Int,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val oraDiInizio: LocalTime?,
    val durataMs: Long,
)
