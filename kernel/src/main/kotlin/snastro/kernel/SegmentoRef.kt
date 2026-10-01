package snastro.kernel

/**
 * A `Segmento` across contexts (ADR 0033 §1): a [SegmentoId] is unique only within its `Registrazione`, so the pair is
 * the only exact key once a `Revisione` or a `Riassunto` spans the `Parte`s of an `Incontro`.
 */
public data class SegmentoRef(val registrazioneId: RegistrazioneId, val segmentoId: SegmentoId)
