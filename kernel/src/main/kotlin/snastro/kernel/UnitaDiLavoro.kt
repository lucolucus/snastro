package snastro.kernel

/**
 * Port: runs a command in one transaction (ADR 0012).
 * - The transaction commits when [blocco] returns [Esito.Ok]. It rolls back when [blocco] returns
 *   [Esito.Errore] (returned as is) or throws (the exception propagates).
 * - A nested call joins the enclosing transaction. The first nested [Esito.Errore] or exception
 *   DOOMS it: the outermost call rolls back ALL effects, whatever its own block returns. Then:
 *   - the outermost block returned its own [Esito.Errore] (e.g. it translated a caught infra fault,
 *     ADR 0003) → that Errore is returned;
 *   - it returned [Esito.Ok] over a nested [Esito.Errore] → the first nested Errore is returned, unchanged;
 *   - it returned [Esito.Ok] after catching a nested exception → `IllegalStateException` is thrown,
 *     with that nested exception as its `cause` (it never reports a success it did not commit).
 */
public interface UnitaDiLavoro {
    public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T>
}
