package snastro.kernel

/**
 * Port: runs a read on ONE consistent snapshot, read-only (ADR 0029). It shares one per-thread
 * state with the [UnitaDiLavoro] of the same implementation:
 * - the outermost call opens a read transaction (in SQL: `BEGIN DEFERRED` + `PRAGMA query_only = 1`);
 * - a call nested in `inTransazione` or in another `inLettura` joins it (and sees its uncommitted writes);
 * - `inTransazione` called inside a read throws [IllegalStateException] before running its block;
 * - a write inside a read fails hard and leaves no effect;
 * - an exception thrown by a read nested in `inTransazione` dooms that transaction (the [UnitaDiLavoro] rule);
 * - whatever the outcome, the read mode never outlives the outermost call.
 *
 * It returns [T], not [Esito]: a read has no expected domain failure. Infra faults throw (ADR 0003).
 */
public interface LetturaCoerente {
    public fun <T> inLettura(blocco: () -> T): T
}
