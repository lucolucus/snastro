package snastro.progetto.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalTime

/**
 * Published Language of the domain event `OraDiInizioModificata` (boundary `eventi-progetto-incontro`, ADR 0033 §3,
 * after commit): the user set, changed or cleared the OraDiInizio of a Parte; `null` is the empty time (INV-I14).
 */
public data class OraDiInizioModificata(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val precedente: LocalTime?,
    val nuova: LocalTime?,
) : EventoPubblicato
