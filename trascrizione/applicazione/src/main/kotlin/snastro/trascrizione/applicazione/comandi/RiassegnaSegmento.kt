package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Moves [segmento] to [destinazione] (`null` = a NEW Voce), on the Trascritto of [registrazioneId]
 * (actor: utente). See [RiassegnaSegmentoServizio].
 */
public data class RiassegnaSegmento(
    val registrazioneId: RegistrazioneId,
    val segmento: SegmentoId,
    val destinazione: VoceId?,
)
