package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Moves [segmento] to [destinazione] (`null` = a NEW Voce), on the Trascritto of [registrazioneId]
 * (actor: utente). See [RiassegnaSegmentoServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its [snastro.kernel.VoceRef]s from, when it knows it; a
 * Voce of another Incontro is `VoceNonTrovata`, nothing changes (the root only sees `VoceId`s). `null` = not stated.
 */
public data class RiassegnaSegmento(
    val registrazioneId: RegistrazioneId,
    val segmento: SegmentoId,
    val destinazione: VoceId?,
    val incontroDelleVoci: IncontroId? = null,
)
