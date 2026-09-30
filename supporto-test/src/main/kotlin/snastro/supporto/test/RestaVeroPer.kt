package snastro.supporto.test

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val INTERVALLO = 10.milliseconds

/**
 * The negative twin of [attendiFinche] (CR-19a): asserts that [condizione] stays true for the whole [durata]
 * ("nothing happens meanwhile"), checking it repeatedly and failing with [messaggio] as soon as it turns false.
 * Replaces a fixed `Thread.sleep` followed by ONE check.
 */
public fun restaVeroPer(durata: Duration, messaggio: String, condizione: () -> Boolean) {
    val scadenza = TimeSource.Monotonic.markNow() + durata
    do {
        if (!condizione()) throw AssertionError("$messaggio (diventata falsa prima di $durata)")
        Thread.sleep(INTERVALLO.inWholeMilliseconds)
    } while (!scadenza.hasPassedNow())
    if (!condizione()) throw AssertionError("$messaggio (falsa allo scadere di $durata)")
}
