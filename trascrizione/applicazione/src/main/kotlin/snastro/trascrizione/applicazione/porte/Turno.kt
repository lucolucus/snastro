package snastro.trascrizione.applicazione.porte

import snastro.kernel.IntervalloMs

/**
 * One diarized turn: [voceIndice] is the diarizer's cluster index for this run only — transient, never
 * persisted; the `Trascritto` maps it to a `VoceId` by first appearance.
 */
public data class Turno(val intervallo: IntervalloMs, val voceIndice: Int) {
    init {
        require(voceIndice >= 0) { "voceIndice negativo: $voceIndice" }
    }
}
