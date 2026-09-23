package snastro.trascrizione.applicazione.comandi

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Splits [segmenti] off [origine] into a new Voce, on the Trascritto of [registrazioneId] (actor: utente).
 * See [DividiVoceServizio].
 */
public data class DividiVoce(val registrazioneId: RegistrazioneId, val origine: VoceId, val segmenti: Set<SegmentoId>)
