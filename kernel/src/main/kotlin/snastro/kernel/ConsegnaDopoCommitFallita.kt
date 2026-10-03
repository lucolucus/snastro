package snastro.kernel

/**
 * Thrown by `inTransazione` when the command COMMITTED but at least one [AbbonatoDopoCommit] then failed
 * (e.g. a view refresh): the writes are durable, only a follow-up did not run. [cause] is the first
 * failure, the others are attached as suppressed. A failure of the command or of its commit is never
 * this type (ADR 0012).
 */
public class ConsegnaDopoCommitFallita(causa: Throwable) :
    RuntimeException("comando confermato, ma un abbonato dopo-commit e fallito", causa)
