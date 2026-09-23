package snastro.trascrizione.applicazione.porte

import snastro.kernel.IntervalloMs

/** One aligned piece of speech of the diarizer's voice [voceIndice]; overlaps with other voices are kept (INV-7). */
public data class SegmentoGrezzo(val voceIndice: Int, val intervallo: IntervalloMs, val testo: String)
