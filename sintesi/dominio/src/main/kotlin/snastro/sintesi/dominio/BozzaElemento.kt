package snastro.sintesi.dominio

/**
 * One raw element of a [BozzaRiassunto]: [fonti] are input labels k (INV-I19), mapped back to Segmenti through the
 * run's label table; [voce] is the Responsabile of an Azione or the speaker of a PuntoChiave (ignored for the other
 * kinds).
 */
public data class BozzaElemento(val testo: String, val fonti: List<Int>, val voce: Int?)
