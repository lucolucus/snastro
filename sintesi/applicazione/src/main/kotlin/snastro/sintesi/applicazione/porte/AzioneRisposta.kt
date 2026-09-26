package snastro.sintesi.applicazione.porte

/** An Azione as answered by the model; [fonti] = segmentoId numbers, [responsabile] = voceId number, unverified. */
public data class AzioneRisposta(val testo: String, val fonti: List<Int>, val responsabile: Int?)
