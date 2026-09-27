package snastro.supporto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * A child scope of [genitore] (ADR 0028 §2): a [SupervisorJob] tied to the parent's [Job], so a failing
 * `launch` goes to [gestore] and cancels neither the parent nor its siblings, while cancelling the parent
 * cancels the child. With [dispatcher] null the child runs on the parent's dispatcher.
 */
public fun figlioDi(
    genitore: CoroutineScope,
    dispatcher: CoroutineDispatcher? = null,
    gestore: CoroutineExceptionHandler,
): CoroutineScope {
    val contesto = genitore.coroutineContext + SupervisorJob(genitore.coroutineContext[Job]) + gestore
    return CoroutineScope(if (dispatcher == null) contesto else contesto + dispatcher)
}
