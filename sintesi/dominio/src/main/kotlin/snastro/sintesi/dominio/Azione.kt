package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** Something to do, with its verified Fonti (INV-S4: never empty) and an optional Responsabile (a Voce). */
public data class Azione(val testo: TestoConVoci, val fonti: Set<SegmentoId>, val responsabile: VoceId?) {
    init {
        require(fonti.isNotEmpty()) { "un Azione ha almeno una Fonte" }
    }
}
