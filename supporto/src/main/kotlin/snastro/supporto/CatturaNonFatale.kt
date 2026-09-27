package snastro.supporto

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [blocco] and catches every non-fatal failure as [Result.failure]. It rethrows
 * [CancellationException] (cooperative cancellation) and [VirtualMachineError] (OOM, stack overflow):
 * the one sanctioned catch-all at the edges (ADR 0028 §2, CR-7).
 */
@Suppress("TooGenericExceptionCaught") // CR-7: the sanctioned catch-all; fatal throwables are rethrown above.
public inline fun <T> catturaNonFatale(blocco: () -> T): Result<T> =
    try {
        Result.success(blocco())
    } catch (e: CancellationException) {
        throw e
    } catch (e: VirtualMachineError) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
