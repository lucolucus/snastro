package snastro.sintesi.dominio

import snastro.kernel.VoceId

/** A piece of a [TestoConVoci]: plain text, or a speaker as a Voce reference (INV-S5: never a Nome). */
public sealed interface ParteTesto {
    public data class Testo(val testo: String) : ParteTesto

    public data class Voce(val voceId: VoceId) : ParteTesto
}
