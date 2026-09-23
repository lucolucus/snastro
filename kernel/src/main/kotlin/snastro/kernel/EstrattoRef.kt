package snastro.kernel

/**
 * A short excerpt of a `Registrazione`: its [intervalli] are played as one sequence.
 * Non-empty, ordered by `inizioMs`, total duration at most [DURATA_MASSIMA_MS] (`require`).
 */
public data class EstrattoRef(val registrazioneId: RegistrazioneId, val intervalli: List<IntervalloMs>) {
    init {
        require(intervalli.isNotEmpty()) { "EstrattoRef senza intervalli" }
        require(intervalli.zipWithNext().all { (a, b) -> a.inizioMs <= b.inizioMs }) {
            "intervalli non ordinati per inizioMs: $intervalli"
        }
        val durataMs = intervalli.sumOf { it.fineMs - it.inizioMs }
        require(durataMs <= DURATA_MASSIMA_MS) { "estratto di $durataMs ms oltre $DURATA_MASSIMA_MS ms" }
    }

    public companion object {
        /** Upper bound of the total duration of an excerpt. */
        public const val DURATA_MASSIMA_MS: Long = 10_000
    }
}
