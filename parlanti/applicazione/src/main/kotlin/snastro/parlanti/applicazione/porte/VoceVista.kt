package snastro.parlanti.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.VoceRef

/**
 * One Voce as read through [LettoreVoci]: [intervalli] are those of its current Segmenti, ordered by
 * inizio (tie: segmentoId). Never the text — Parlanti reads intervals only.
 */
public data class VoceVista(
    val voceRef: VoceRef,
    val intervalli: List<IntervalloMs>,
)
