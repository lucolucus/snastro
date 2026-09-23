package snastro.trascrizione.applicazione.porte

import snastro.kernel.IntervalloMs

/** One recognized token; [intervallo] is relative to the start of the samples given to the recognizer. */
public data class Token(val testo: String, val intervallo: IntervalloMs)
