package snastro.kernel

/**
 * An expected business failure, carried by [Esito.Errore] — never a `Throwable` (ADR 0003, CR-8).
 * Plain, NOT sealed (Kotlin forbids sealed subtypes across modules): each context declares ONE
 * sealed hierarchy `Errore<Contesto> : ErroreDominio` in its `Errori<Contesto>.kt`.
 */
public interface ErroreDominio
