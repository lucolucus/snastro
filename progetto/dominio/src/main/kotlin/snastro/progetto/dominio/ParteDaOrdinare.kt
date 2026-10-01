package snastro.progetto.dominio

import snastro.kernel.RegistrazioneId
import java.time.Instant
import java.time.LocalDate

/** A Parte of an Incontro as [OrdineDelleParti] reads it: the fields of its order key (INV-I2). */
public data class ParteDaOrdinare(
    val registrazioneId: RegistrazioneId,
    val dataRegistrazione: LocalDate,
    val oraDiInizio: OraDiInizio?,
    val aggiuntaAlle: Instant,
)
