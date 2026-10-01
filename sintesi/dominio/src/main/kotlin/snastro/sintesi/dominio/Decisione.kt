package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef

/** Something decided, with its verified Fonti (INV-S4: never empty). */
public data class Decisione(val testo: TestoConVoci, val fonti: Set<SegmentoRef>) {
    init {
        require(fonti.isNotEmpty()) { "una Decisione ha almeno una Fonte" }
    }
}
