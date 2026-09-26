package snastro.sintesi.dominio

/**
 * One raw element of a [BozzaRiassunto]: [fonti] are segmentoId numbers; [voce] is the Responsabile of an
 * Azione or the speaker of a PuntoChiave (ignored for the other kinds).
 */
public data class BozzaElemento(val testo: String, val fonti: List<Int>, val voce: Int?)
