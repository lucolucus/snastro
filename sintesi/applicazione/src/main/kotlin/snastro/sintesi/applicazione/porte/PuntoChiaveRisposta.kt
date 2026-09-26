package snastro.sintesi.applicazione.porte

/** A PuntoChiave as answered by the model; [fonti] = segmentoId numbers, [parlante] = voceId number, unverified. */
public data class PuntoChiaveRisposta(val testo: String, val fonti: List<Int>, val parlante: Int?)
