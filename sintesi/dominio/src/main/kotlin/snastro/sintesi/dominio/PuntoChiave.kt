package snastro.sintesi.dominio

import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId

/** A key point, with its verified Fonti (INV-S4: never empty) and an optional speaker (the Voce of one of them). */
public data class PuntoChiave(val testo: TestoConVoci, val fonti: Set<SegmentoRef>, val parlante: VoceId?) {
    init {
        require(fonti.isNotEmpty()) { "un PuntoChiave ha almeno una Fonte" }
    }
}
