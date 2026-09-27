package snastro.supporto.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Runs [blocco] with a fresh scope (real threads, [Dispatchers.Default], [SupervisorJob]) and cancels that
 * scope in `finally`, even when an assertion fails (ADR 0028 §3). Under `runTest`, use `backgroundScope`:
 * it already runs on the test's `StandardTestDispatcher` and is cancelled at the end of the test.
 */
public fun <T> conScopeDiProva(blocco: (CoroutineScope) -> T): T {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        return blocco(scope)
    } finally {
        scope.cancel()
    }
}
