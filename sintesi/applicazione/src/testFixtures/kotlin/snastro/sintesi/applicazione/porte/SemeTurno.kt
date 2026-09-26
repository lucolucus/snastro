package snastro.sintesi.applicazione.porte

import snastro.kernel.IntervalloMs

/**
 * One diarized, transcribed turn an Elaborazione produces, as seeded by [LettoreTrascrittoContratto]:
 * [voceIndice] is the diarizer's voice label, [testo] the text as transcribed. Every seeded turn ends
 * within the first 60 s. The supplier mints the ids (see [SegmentoConiato]).
 */
public data class SemeTurno(
    val voceIndice: Int,
    val intervallo: IntervalloMs,
    val testo: String,
)
