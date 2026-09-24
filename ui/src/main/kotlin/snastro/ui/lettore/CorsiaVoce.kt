package snastro.ui.lettore

import snastro.kernel.VoceId

/**
 * AC-579: one voice lane drawn above [BarraLettore]'s scrubber track — a pure VIEW projection of a
 * Segmento already in `RegistrazioneUiStato` (no new source, no presenter change): [inizioMs]/[fineMs]
 * place it, [voceId] picks its colour via [snastro.ui.palette]. The embedding screen builds the list;
 * [BarraLettore] itself stays agnostic of Segmenti (it is shared by every screen with a player bar).
 */
data class CorsiaVoce(val voceId: VoceId, val inizioMs: Long, val fineMs: Long)
