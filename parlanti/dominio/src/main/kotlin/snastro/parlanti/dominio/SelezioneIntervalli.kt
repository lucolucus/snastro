package snastro.parlanti.dominio

import snastro.kernel.IntervalloMs

/**
 * Audio budget of a print source ([SorgenteImpronta.di]). PROVISIONAL: spike
 * `impronta-vocale-affidabilita` calibrates it (ADR 0012 Amendment (b) point 1). Changing it needs
 * no migration: every stored `chiave` goes stale and the prints are re-derived.
 */
public const val BUDGET_IMPRONTA_MS: Long = 30_000L

/** Intervals shorter than this are dropped, unless none reaches it (then the single longest is used). */
public const val DURATA_MINIMA_SEGMENTO_MS: Long = 1_000L

/** Audio budget of an `EstrattoAudio` (same rule as the print source). */
public const val BUDGET_ESTRATTO_MS: Long = 10_000L

/** Interval cap of an `EstrattoAudio`. */
public const val MAX_INTERVALLI_ESTRATTO: Int = 3

/**
 * The ONE selection rule for bounded audio of a `Voce` (ADR 0012 Amendment (b) point 1), shared by
 * [SorgenteImpronta] and `EstrattoAudio`. Pure and deterministic:
 * 1. overlapping [intervalli] are merged into their union (a `Voce` after `unire` may overlap);
 * 2. keep those of at least [DURATA_MINIMA_SEGMENTO_MS], else the single longest;
 * 3. longest first, tie: earlier `inizioMs`;
 * 4. accumulate up to [budgetMs] and at most [maxIntervalli] (`null` = no cap); the interval that
 *    crosses the budget is trimmed from its start to `[inizio, inizio + resto]`;
 * 5. the result is disjoint and in time order. Empty input gives an empty result.
 */
public fun selezionaIntervalli(intervalli: List<IntervalloMs>, budgetMs: Long, maxIntervalli: Int?): List<IntervalloMs> {
    require(budgetMs > 0) { "budgetMs deve essere positivo: $budgetMs" }
    require(maxIntervalli == null || maxIntervalli > 0) { "maxIntervalli deve essere positivo: $maxIntervalli" }
    val uniti = unisciSovrapposti(intervalli)
    val candidati = uniti.filter { it.durataMs >= DURATA_MINIMA_SEGMENTO_MS }
        .ifEmpty { listOfNotNull(uniti.sortedWith(PIU_LUNGO_PRIMA).firstOrNull()) }
    val scelti = mutableListOf<IntervalloMs>()
    var resto = budgetMs
    for (intervallo in candidati.sortedWith(PIU_LUNGO_PRIMA)) {
        if (resto == 0L || (maxIntervalli != null && scelti.size == maxIntervalli)) break
        val preso = if (intervallo.durataMs <= resto) intervallo else IntervalloMs(intervallo.inizioMs, intervallo.inizioMs + resto)
        scelti += preso
        resto -= preso.durataMs
    }
    return scelti.sortedBy { it.inizioMs }
}

private val IntervalloMs.durataMs: Long get() = fineMs - inizioMs

private val PIU_LUNGO_PRIMA: Comparator<IntervalloMs> =
    compareByDescending<IntervalloMs> { it.durataMs }.thenBy { it.inizioMs }

/** Union of overlapping intervals, in time order. Touching half-open intervals are already disjoint. */
private fun unisciSovrapposti(intervalli: List<IntervalloMs>): List<IntervalloMs> {
    val uniti = mutableListOf<IntervalloMs>()
    for (intervallo in intervalli.sorted()) {
        val ultimo = uniti.lastOrNull()
        if (ultimo != null && intervallo.inizioMs < ultimo.fineMs) {
            uniti[uniti.lastIndex] = IntervalloMs(ultimo.inizioMs, maxOf(ultimo.fineMs, intervallo.fineMs))
        } else {
            uniti += intervallo
        }
    }
    return uniti
}
