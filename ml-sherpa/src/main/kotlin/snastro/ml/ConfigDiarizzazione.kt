package snastro.ml

/**
 * FastClustering/pyannote knobs of one [diarizza] run (boundary tec-ml-sherpa; values fixed by ADR
 * 0014). [numeroCluster] = -1 requests automatic threshold clustering (FastClustering cuts at
 * [soglia]); a positive count requests that exact `cutree_k` — sherpa never rejects a count larger
 * than the audio's real embeddings, it silently returns at most that many `speaker`s (measured,
 * spike `scelta-diarizzatore` 2026-09-24, `DiarizzatoreSherpaTest`).
 */
public data class ConfigDiarizzazione(
    public val numeroCluster: Int,
    public val soglia: Float,
    public val rapportoSpostamentoFinestra: Float,
    public val minDurataAttivaS: Float,
    public val minDurataInattivaS: Float,
)
