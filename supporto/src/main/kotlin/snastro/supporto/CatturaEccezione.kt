package snastro.supporto

/**
 * Runs [blocco] and catches an [Exception] as [Result.failure]. It rethrows `CancellationException` and every
 * [Error] (an `AssertionError` included, not only a [VirtualMachineError]): the edge catch for a committing command,
 * where an `Error` after the commit is fatal and must propagate (ADR 0041 §1, D-0065). The twin of [catturaNonFatale],
 * which stays the catch for every other edge; built on it, so the one catch-all of the edges stays there.
 */
public inline fun <T> catturaEccezione(blocco: () -> T): Result<T> =
    catturaNonFatale(blocco).onFailure { if (it !is Exception) throw it }
