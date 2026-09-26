package snastro.sintesi.applicazione.letture

/**
 * AC-S103: one Voce as the Riassunto tab shows it. [etichetta] is always "Voce n"; [nome] is the
 * CURRENT Nome when the Voce is attributed, else `null` (colour = `palette(voceId)`, decided in `:ui`).
 */
public data class VoceVista(val voceId: Int, val etichetta: String, val nome: String?)
