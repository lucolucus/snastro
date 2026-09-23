package snastro.parlanti.applicazione.porte

import snastro.kernel.IntervalloMs

/**
 * One diarized turn the Elaborazione produces, as seeded by a [LettoreVociContratto]: [voceIndice] is
 * the diarizer's voice label. No text: Parlanti never reads it (the real supplier may transcribe any
 * text). The supplier mints the `SegmentoId` and `VoceId` (see [SegmentoConiato]).
 */
public data class SemeTurno(
    val voceIndice: Int,
    val intervallo: IntervalloMs,
)
