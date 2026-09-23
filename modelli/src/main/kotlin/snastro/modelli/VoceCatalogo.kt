package snastro.modelli

/**
 * One entry of the model catalogue (ADR 0008 Amendment (c)): the downloaded **asset** ([url],
 * [sha256] and [dimensioneByte] of the asset itself — the archive, or the single file — never of
 * the installed/extracted content) plus its [formato] and licence/attribution. [id] is minted by
 * the spike ADR that chooses the model (e.g. `"segmentazione-pyannote-3.0"`) and stays stable
 * across catalogue edits of licence/attribution text, but **never** across a change of [sha256]:
 * different bytes mint a new id (never reused — the installed directory name and its `.sha256`
 * marker both key off it).
 *
 * [id] also names the installed directory (`<cartella>/<id>/`) and the temporary files used while
 * installing it, so it is restricted to a safe, portable set of path characters — a programmer
 * error (a malformed catalogue entry), never user input.
 */
public data class VoceCatalogo(
    public val id: String,
    public val ruolo: String,
    public val url: String,
    public val sha256: String,
    public val dimensioneByte: Long,
    public val formato: FormatoVoce,
    public val licenza: String,
    public val attribuzione: String,
) {
    init {
        require(ID_PATTERN.matches(id)) { "id di VoceCatalogo non valido: '$id' (atteso ${ID_PATTERN.pattern})" }
    }

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9._-]+")
    }
}
