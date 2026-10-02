package snastro.parlanti.applicazione.porte

import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * One Parte of an Incontro as read through [LettoreRegistrazione.parti]: its [numero] is its 1-based rank in the
 * Incontro's order ([INV-I2], decided by Progetto), [dataRegistrazione] its current date — the first Parte's is the
 * Incontro's date ('Ospite del <data>', [INV-19]).
 */
public data class ParteDiIncontroParlanti(
    val registrazioneId: RegistrazioneId,
    val numero: Int,
    val dataRegistrazione: LocalDate,
)
