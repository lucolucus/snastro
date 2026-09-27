package snastro.supporto.test

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val INTERVALLO = 10.milliseconds

/**
 * The only polling wait of the tests (ADR 0028 §3): returns as soon as [condizione] is true, otherwise
 * fails with an [AssertionError] carrying [messaggio] once [timeout] has elapsed.
 */
public fun attendiFinche(timeout: Duration = 5.seconds, messaggio: String, condizione: () -> Boolean) {
    val scadenza = TimeSource.Monotonic.markNow() + timeout
    while (!condizione()) {
        if (scadenza.hasPassedNow()) throw AssertionError("$messaggio (non vero entro $timeout)")
        Thread.sleep(INTERVALLO.inWholeMilliseconds)
    }
}
