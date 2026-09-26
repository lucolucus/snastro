package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId

/** A key point, with its verified Fonti (INV-S4: never empty) and an optional speaker (the Voce of one of them). */
public data class PuntoChiave(val testo: TestoConVoci, val fonti: Set<SegmentoId>, val parlante: VoceId?) {
    init {
        require(fonti.isNotEmpty()) { "un PuntoChiave ha almeno una Fonte" }
    }
}
