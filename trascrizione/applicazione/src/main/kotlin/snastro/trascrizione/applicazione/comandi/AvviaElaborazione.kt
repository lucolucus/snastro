package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId

/**
 * Enqueues a new `Elaborazione` `in_attesa` for [registrazioneId] (actors: the RegistrazioneAggiunta
 * sync subscriber and the user's retry after `fallita`). See [AvviaElaborazioneServizio].
 */
public data class AvviaElaborazione(val registrazioneId: RegistrazioneId)
