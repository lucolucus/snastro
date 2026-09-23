package snastro.ui.lettore

import snastro.kernel.RegistrazioneId

/**
 * `tec-lettore-audio`: the [LettoreAudio]'s playback state. [registrazioneId] is `null` when nothing
 * has ever played; [posizioneMs] is the position within the source being played (a plain
 * `riproduciDa`) or within the rebuilt excerpt (a `riproduciEstratto`, AC-188).
 */
data class StatoLettore(val registrazioneId: RegistrazioneId?, val posizioneMs: Long, val inRiproduzione: Boolean)
