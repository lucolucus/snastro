package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/** Something to do, with its verified Fonti (INV-S4: never empty) and an optional Responsabile (a Voce). */
public data class Azione(val testo: TestoConVoci, val fonti: Set<SegmentoRef>, val responsabile: VoceId?) {
    init {
        require(fonti.isNotEmpty()) { "un Azione ha almeno una Fonte" }
    }
}
