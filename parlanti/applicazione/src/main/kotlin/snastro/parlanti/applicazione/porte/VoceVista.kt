package snastro.parlanti.applicazione.porte

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef

/**
 * One Voce of an Incontro as read through [LettoreVoci]: [intervalliPerParte] has one entry per Parte where it has
 * current Segmenti (never an empty list from the supplier), each ordered by inizio (tie: segmentoId). Never the
 * text — Parlanti reads intervals only.
 */
public data class VoceVista(
    val voceRef: VoceRef,
    val intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>,
)
