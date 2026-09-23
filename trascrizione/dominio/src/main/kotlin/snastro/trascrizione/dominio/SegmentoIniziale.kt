package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs

/** One diarized, transcribed turn handed to [Trascritto.crea]; [voceIndice] is the diarizer's voice label. */
public data class SegmentoIniziale(val voceIndice: Int, val intervallo: IntervalloMs, val testo: String)
