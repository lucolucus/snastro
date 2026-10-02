package snastro.trascrizione.applicazione.comandi

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/**
 * Splits [segmenti] off [origine] into a new Voce, on the Trascritto of [registrazioneId] (actor: utente).
 * See [DividiVoceServizio].
 * [incontroDelleVoci] (INV-I7): the Incontro the caller read its refs from, when it knows it; another Incontro is
 * `SegmentoNonTrovato` of the first of [segmenti] as a `SegmentoRef` of this Parte (`VoceNonTrovata` of [origine] when
 * [segmenti] is empty), refused before the root is touched; nothing changes. `null` = not stated.
 */
public data class DividiVoce(
    val registrazioneId: RegistrazioneId,
    val origine: VoceId,
    val segmenti: Set<SegmentoId>,
    val incontroDelleVoci: IncontroId? = null,
)
