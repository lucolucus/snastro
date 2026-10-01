package snastro.kernel

/**
 * Cross-context correlation key of a `Voce` (ADR 0033 §1, D-0002): the `Incontro` it belongs to plus its [VoceId], the
 * "Voce n" number, unique in the Incontro and never reused there.
 */
public data class VoceRef(val incontroId: IncontroId, val voceId: VoceId)
