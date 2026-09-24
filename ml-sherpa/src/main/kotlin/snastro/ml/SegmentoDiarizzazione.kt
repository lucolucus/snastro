package snastro.ml

/**
 * One raw sherpa-onnx `OfflineSpeakerDiarization` segment (boundary tec-ml-sherpa): [inizioS] /
 * [fineS] in seconds — sherpa's own unit, not yet rounded to ms — and [speaker], the FastClustering
 * cluster index for this run (>= 0). `com.k2fsa` never crosses `:ml-sherpa` (ADR 0004): this is the
 * boundary's own shape, mapped by the consumer (e.g. `DiarizzatoreSherpa`) into its port's types.
 */
public data class SegmentoDiarizzazione(public val inizioS: Float, public val fineS: Float, public val speaker: Int)
