package snastro.sintesi.applicazione.porte

/** A Decisione or QuestioneAperta as answered by the model; [fonti] = segmentoId numbers, unverified. */
public data class ElementoRisposta(val testo: String, val fonti: List<Int>)
