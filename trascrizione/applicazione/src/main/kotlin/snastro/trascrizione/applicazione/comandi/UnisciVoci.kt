package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * Merges [rimossa] into [sopravvive] on the Trascritto of [registrazioneId] (actor: utente). See
 * [UnisciVociServizio].
 */
public data class UnisciVoci(val registrazioneId: RegistrazioneId, val sopravvive: VoceId, val rimossa: VoceId)
