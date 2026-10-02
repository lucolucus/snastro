package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId

/**
 * Merges [rimossa] into [sopravvive] on the Trascritto of [registrazioneId] (actor: utente). See
 * [UnisciVociServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its [snastro.kernel.VoceRef]s from, when it knows it; a
 * Voce of another Incontro is `VoceNonTrovata`, nothing changes (the root only sees `VoceId`s). `null` = not stated.
 */
public data class UnisciVoci(
    val registrazioneId: RegistrazioneId,
    val sopravvive: VoceId,
    val rimossa: VoceId,
    val incontroDelleVoci: IncontroId? = null,
)
