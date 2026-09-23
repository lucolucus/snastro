package snastro.trascrizione.applicazione.porte

/** What the recognizer heard: the whole [testo] and its [token], or `null` if the model gives no timestamps. */
public data class Riconoscimento(val testo: String, val token: List<Token>?)
