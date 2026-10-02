package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Splits [segmenti] off [origine] into a new Voce, on the Trascritto of [registrazioneId] (actor: utente).
 * See [DividiVoceServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its [snastro.kernel.VoceRef]s from, when it knows it; a
 * Voce of another Incontro is `VoceNonTrovata`, nothing changes (the root only sees `VoceId`s). `null` = not stated.
 */
public data class DividiVoce(
    val registrazioneId: RegistrazioneId,
    val origine: VoceId,
    val segmenti: Set<SegmentoId>,
    val incontroDelleVoci: IncontroId? = null,
)
