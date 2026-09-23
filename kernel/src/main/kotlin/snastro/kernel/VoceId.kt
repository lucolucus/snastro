package snastro.kernel

/** Identity of a `Voce` inside its `Trascritto`: equals the n of the label "Voce n"; never reused, never renumbered. */
@JvmInline
public value class VoceId(public val numero: Int)
