package snastro.ui.registrazione

import snastro.kernel.ParlanteId

/** Who "questa frase" is: an `attivo` Parlante, or 'nuovo…' (Q-7: a Nome, ricorrente unless occasionale). */
sealed interface ObiettivoNome {
    data class Esistente(val parlanteId: ParlanteId) : ObiettivoNome

    data class Nuovo(val nome: String, val ricorrente: Boolean) : ObiettivoNome
}
