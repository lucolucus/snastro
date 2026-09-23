package snastro.kernel

/**
 * Port: runs a command in one transaction (ADR 0012). The transaction is committed when [blocco]
 * returns [Esito.Ok] and rolled back when it returns [Esito.Errore] or throws (the exception
 * propagates). A nested call joins the enclosing transaction.
 */
public interface UnitaDiLavoro {
    public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T>
}
