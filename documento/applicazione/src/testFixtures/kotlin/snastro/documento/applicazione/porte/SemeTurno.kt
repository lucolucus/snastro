package snastro.documento.applicazione.porte

import snastro.kernel.IntervalloMs

/**
 * One diarized, transcribed turn the Elaborazione produces, as seeded by a
 * [LettoreTrascrittoContratto]: [voceIndice] is the diarizer's voice label, [testo] the text as
 * transcribed. The supplier mints the `SegmentoId` and `VoceId` (see [SegmentoConiato]).
 */
public data class SemeTurno(
    val voceIndice: Int,
    val intervallo: IntervalloMs,
    val testo: String,
)
