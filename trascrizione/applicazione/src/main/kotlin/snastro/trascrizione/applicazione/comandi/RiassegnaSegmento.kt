package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Moves [segmento] to [destinazione] (`null` = a NEW Voce), on the Trascritto of [registrazioneId]
 * (actor: utente). See [RiassegnaSegmentoServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its refs from, when it knows it; another Incontro is
 * `SegmentoNonTrovato` of [segmento] as a `SegmentoRef` of this Parte, refused before the root is touched (even with
 * [destinazione] `null`); nothing changes. `null` = not stated.
 */
public data class RiassegnaSegmento(
    val registrazioneId: RegistrazioneId,
    val segmento: SegmentoId,
    val destinazione: VoceId?,
    val incontroDelleVoci: IncontroId? = null,
)
