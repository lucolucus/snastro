package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/** One Parte's Trascritto with its text, carrying the [incontroId] it belongs to (boundary `api-voci-incontro`). */
public data class TrascrittoTesto(
    val registrazioneId: RegistrazioneId,
    val incontroId: IncontroId,
    val segmenti: List<SegmentoVista>,
)
