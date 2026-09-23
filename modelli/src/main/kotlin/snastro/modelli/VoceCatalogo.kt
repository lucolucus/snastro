package snastro.modelli

/**
 * One entry of the model catalogue (ADR 0008): a single file (URL + pinned SHA-256) plus its
 * licence and attribution. [id] is minted by the spike ADR that chooses the model (e.g.
 * `"segmentazione-pyannote-3.0"`) and stays stable across catalogue edits.
 */
public data class VoceCatalogo(
    public val id: String,
    public val ruolo: String,
    public val url: String,
    public val sha256: String,
    public val dimensioneByte: Long,
    public val licenza: String,
    public val attribuzione: String,
)
