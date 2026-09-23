package snastro.kernel

/** Outcome of an operation that can fail for an expected business reason (ADR 0003). */
public sealed interface Esito<out T> {
    public data class Ok<T>(val valore: T) : Esito<T>

    public data class Errore(val errore: ErroreDominio) : Esito<Nothing>
}

/** Chains the next step on [Esito.Ok]; the first [Esito.Errore] is propagated and later steps never run. */
public inline fun <T, R> Esito<T>.poi(passo: (T) -> Esito<R>): Esito<R> =
    when (this) {
        is Esito.Ok -> passo(valore)
        is Esito.Errore -> this
    }

/** Transforms the value of an [Esito.Ok]; an [Esito.Errore] is returned unchanged. */
public inline fun <T, R> Esito<T>.mappa(trasforma: (T) -> R): Esito<R> =
    when (this) {
        is Esito.Ok -> Esito.Ok(trasforma(valore))
        is Esito.Errore -> this
    }

/** Runs [azione] on the error of an [Esito.Errore] (side effect only) and returns this [Esito] unchanged. */
public inline fun <T> Esito<T>.seErrore(azione: (ErroreDominio) -> Unit): Esito<T> {
    if (this is Esito.Errore) azione(errore)
    return this
}
