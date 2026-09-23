package snastro.kernel

/**
 * Port: runs a command in one transaction (ADR 0012).
 * - The transaction commits when [blocco] returns [Esito.Ok]. It rolls back when [blocco] returns
 *   [Esito.Errore] (returned as is) or throws (the exception propagates).
 * - A nested call joins the enclosing transaction. A nested [Esito.Errore] or exception DOOMS it:
 *   the outermost call rolls back ALL effects and returns the first nested [Esito.Errore] even if
 *   its own block returned [Esito.Ok]. If the nested exception was caught by the outer block, the
 *   outermost call throws `IllegalStateException` after the rollback (it never reports a success
 *   it did not commit).
 */
public interface UnitaDiLavoro {
    public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T>
}
