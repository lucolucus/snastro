package snastro.kernel

/**
 * Half-open time interval `[inizioMs, fineMs)` in milliseconds from the start of a `Registrazione`.
 * `0 <= inizioMs < fineMs` is a programmer-error invariant (`require`); intervals are ordered
 * numerically, by [inizioMs] then [fineMs].
 */
public data class IntervalloMs(val inizioMs: Long, val fineMs: Long) : Comparable<IntervalloMs> {
    init {
        require(inizioMs >= 0) { "inizioMs negativo: $inizioMs" }
        require(inizioMs < fineMs) { "inizioMs ($inizioMs) deve precedere fineMs ($fineMs)" }
    }

    override fun compareTo(other: IntervalloMs): Int = compareValuesBy(this, other, { it.inizioMs }, { it.fineMs })
}
