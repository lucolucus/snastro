package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef

/** Something left open, with its verified Fonti (INV-S4: never empty). */
public data class QuestioneAperta(val testo: TestoConVoci, val fonti: Set<SegmentoRef>) {
    init {
        require(fonti.isNotEmpty()) { "una QuestioneAperta ha almeno una Fonte" }
    }
}
