package snastro.kernel

/**
 * Thrown by `inTransazione` when the command COMMITTED but at least one [AbbonatoDopoCommit] then failed: the
 * writes are durable, only a follow-up did not run. [cause] is the first failure ([prima]), the [altre] are
 * attached as suppressed. A failure of the command or of its commit is never this type (ADR 0012).
 *
 * L261: the constructor shape `(Throwable, List)` is deliberate — kotlinx-coroutines' stack-trace recovery copies
 * an exception crossing `withContext` only through a `(Throwable)`/`(String, Throwable)`/`(String)`/`()`
 * constructor, so this one always arrives as the very instance thrown, its [cause] the subscriber's failure.
 */
public class ConsegnaDopoCommitFallita(prima: Throwable, altre: List<Throwable> = emptyList()) :
    RuntimeException("comando confermato, ma un abbonato dopo-commit e fallito", prima) {
    init {
        altre.forEach(::addSuppressed)
    }
}
