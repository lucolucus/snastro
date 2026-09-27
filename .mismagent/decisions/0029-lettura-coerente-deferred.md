---
scope: global
status: accepted
supersedes: null   # partial, amended in place with dated pointers here: ADR 0012 (Amendment (e): a second kernel port for reads), ADR 0006 (Amendment (c): Amendment (b)'s "every transaction starts BEGIN IMMEDIATE" becomes "every WRITE transaction"), ADR 0022 §4 (RiassuntoRepository.trova reads in a snapshot), architecture.md (kernel row, Transactions and events), code-rules.md (CR-3b), dev-architecture-app.md (#repository, #porta-contratto)
closes_spike: null
decided: 2026-09-27 · user (post-R3 design review, analysis §2.4 X1/X2, §6.2; B1 = a SEPARATE kernel port [user, deviating from the architect's recommendation]; B2–B5 as recommended) · architect (port and contract shape, block split)
enforced_by:
  # PLANNED (not yet an entry: ControlliAdrTest requires the script to exist): block b2-lettura-coerente-migrazione adds
  #   - check: architettura-test/controlli-adr/adr-0029-transazioni-solo-in-persistenza.sh
  #     from: b2-lettura-coerente-migrazione
    # FAIL if a `transaction {`, `transaction(`, `transactionWithResult` call (SQLDelight's Transacter API) appears in any
    # */src/main outside persistenza/ (comment lines stripped). Red on the tree until b2 (TrascrittoRepositorySql:37,
    # ParlanteRepositorySql:84) — hence `from`.
  # Pending delivery (block b2-lettura-coerente-migrazione): script + red-green fixtures, validated via bash -c.
  # Konsist half: code-rules.md CR-3b (in-loop). The read-only guarantee itself is proven by LetturaCoerenteContratto, not grepped.
---
# 0029 — Consistent reads: the kernel port `LetturaCoerente`, BEGIN DEFERRED + read-only, snapshot for multi-table reads

## Context
Since ADR 0006 Amendment (b) (fix-batch-17), every transaction begins `BEGIN IMMEDIATE` through
`DriverSqliteImmediato`. That is the right fix for `SQLITE_BUSY_SNAPSHOT` on writes, but reads pay for it too
(analysis X1):
- `TrascrittoRepositorySql.trova`, the riassunto tab read (`EstensioneR3:151`) and
  `RiallineaTutteLeImpronteServizio` open write transactions only to read;
- readers queue behind writers for up to the 5 s busy_timeout, and the UI can get `SQLITE_BUSY`;
- the pre-R3-1 decision (D-pre-R3-1-a) never weighed DEFERRED.

At the same time, two multi-table `trova`s read with no snapshot at all: `RiassuntoRepositorySql` reads the row, then
its children, and `ParlanteRepositorySql` does the same (X2). These are torn-read candidates of the D-0008 class.

## Decision

### 1. A second kernel port [user B1]
```kotlin
// :kernel  LetturaCoerente.kt
public interface LetturaCoerente {
    /** Runs [blocco] on ONE consistent snapshot, read-only. Infra faults throw (ADR 0003); no Esito. */
    public fun <T> inLettura(blocco: () -> T): T
}
```
- It is separate from `UnitaDiLavoro`, whose signature and contract are **unchanged**. A consumer that only reads
  depends only on `LetturaCoerente` (ISP).
- It returns `T`, not `Esito`: a read has no expected domain failure.

### 2. The semantics across the two ports (binding for every implementation)
The two ports share **one per-thread state**: nesting depth, current mode (none / write / read), and the doom flags.
1. The outermost `inLettura` opens a read transaction: `BEGIN DEFERRED`, then `PRAGMA query_only = 1`.
2. `inLettura` nested in `inTransazione` **joins** it. It sees the transaction's uncommitted writes, sets no pragma,
   and opens nothing.
3. `inLettura` nested in `inLettura` joins.
4. `inTransazione` nested in `inLettura` throws **`IllegalStateException` at once**, before any effect. Upgrading a
   DEFERRED snapshot to a write is exactly the `SQLITE_BUSY_SNAPSHOT` case ADR 0006 (b) removed.
5. A write attempted inside a read transaction **fails hard** [user B2]. In SQL, `query_only` makes it throw
   `SQLITE_READONLY`, and the Finta refuses it too. It leaves no effect.
6. An exception inside a nested `inLettura` **dooms** the enclosing `inTransazione` [user B3]. This is the existing
   nested rule of `UnitaDiLavoro`: a joined participant that throws condemns the whole unit.
7. **No leak.** Whatever the outcome, `query_only` is reset to 0 and the mode returns to "none" before the
   connection goes back. The next outermost `inTransazione` on that thread begins `BEGIN IMMEDIATE`, clean.

### 3. `:persistenza`: one class implements both ports; `DriverSqliteImmediato` is KEPT
- `UnitaDiLavoroSql` becomes `UnitaDiLavoroSql : UnitaDiLavoro, LetturaCoerente`. Its existing `ThreadLocal<Stato>`
  gains the mode, so the state is shared by construction and nothing is duplicated.
- `DriverSqliteImmediato` gains a per-thread begin mode, which `UnitaDiLavoroSql` sets for the outermost read only.
  The mode is **IMMEDIATE by default**, so every write path is byte-identical to today. The code that clears the
  transaction slot when `BEGIN` fails applies to both modes.
- SQLDelight never calls `beginTransaction` for a nested transaction, so rule 2 needs no driver change.
- The composition passes **the same `UnitaDiLavoroSql` instance** as the project's `LetturaCoerente` and as the
  delegate of `DispatcherEventiInMemoria`. `PorteProgetto` builds it once ([ADR 0030](0030-composizione-unica-per-contesto.md)).
  This is what makes rules 2, 4 and 6 hold across the dispatcher's wrapper.
- **The event-publishing wrapper does not wrap `LetturaCoerente`.** A read publishes nothing, and the per-thread
  state it would need lives in `UnitaDiLavoroSql`, which the wrapper already delegates to.
- **Adapters enter a read through the port, never through SQLDelight.** A repository that needs a snapshot receives
  `LetturaCoerente` in its constructor. There is no second `SnastroDatabase.inLettura` entry point, so there is no
  second state to keep in sync.
- **`checkpointDopoCommit()`** [user B5]. `:persistenza` exposes this primitive on the opened database for
  `PRAGMA wal_checkpoint(TRUNCATE)` after the current transaction commits. It replaces
  `ParlanteRepositorySql:84`'s raw `db.transaction { afterCommit { … } }`. ADR 0009/0020's checkpoint obligation is
  unchanged.

### 4. Contract: `LetturaCoerenteContratto` (`:kernel` testFixtures), a separate abstract class
- **Where.** It is a new abstract class. Its `Ambiente` exposes:
  - `lettura: LetturaCoerente` and `unitaDiLavoro: UnitaDiLavoro`, **backed by the same state**;
  - `scrivi(effetto)`, `effetti()` and `leggi(): Set<String>` (a read through `lettura`).
- **Why a separate class**, not cases added to `UnitaDiLavoroContratto`:
  - `UnitaDiLavoroContratto` stays the contract of its own port, unchanged, together with every subclass. It still
    runs against implementations that have no read side (e.g. the dispatcher's wrapper);
  - every new case involves both ports, so its `Ambiente` must expose both. That is a different fixture shape, and
    it belongs to a different class.
- **Cases** (each named `AC-<id> …`, ids pinned by build-manifest):
  1. `inLettura` returns the block's value and sees committed state;
  2. nested in `inTransazione`, it joins and sees the uncommitted write;
  3. nested in `inLettura`, it joins;
  4. `inTransazione` inside `inLettura` throws `IllegalStateException`, with no effect;
  5. a write inside `inLettura` fails, with no effect;
  6. an exception in `inLettura` propagates. The next `inTransazione` commits normally and is not read-only;
  7. an exception in an `inLettura` nested in `inTransazione` dooms the whole transaction (no effect), even if the
     outer block catches it and returns `Ok`.
- **Subclasses:**
  - the Finta's test (`:kernel`);
  - `UnitaDiLavoroSql` in `:persistenza`;
  - `UnitaDiLavoroSql` behind `DispatcherEventiInMemoria.unitaDiLavoro` in `:persistenza`. This proves the wrapper
    needs no read side.
- **SQL-only tests (`:persistenza`):**
  - 8. **snapshot**: a write committed by another thread between two SELECTs of one `inLettura` is not seen;
  - 9. **no queueing**: `inLettura` completes while another thread holds the `BEGIN IMMEDIATE` write lock (latch-driven,
    no sleep, `:supporto-test` helpers).
- **Finta.** `UnitaDiLavoroFinta` implements both ports: one class, one depth counter, one mode. It gains
  `letturaAperta`, alongside the existing `transazioneAperta`, and it refuses `scrivi` while a read is open. It is
  one Finta, not two linked ones.

### 5. The snapshot rule [user B4]
**Every repository read that issues more than one SELECT to build one aggregate or one view runs inside
`lettura.inLettura { }`, inside the repository.** It is never wrapped by `applicazione` or by the composition.
- **Sites:**
  - `TrascrittoRepositorySql.trova` (from its raw `transactionWithResult`);
  - `RiassuntoRepositorySql.trova`;
  - `ParlanteRepositorySql.trova`;
  - the riassunto tab read moves out of `EstensioneR3:151` into the read side it belongs to.
- `RiallineaTutteLeImpronteServizio` reads through `LetturaCoerente` instead of `inTransazione`.
- **Enforcement:**
  - a code-review criterion (`dev-architecture-app.md#repository`);
  - one concurrency case in each multi-table repository's `…RepositoryContratto` SQL subclass: the aggregate read
    while another thread rewrites it is always the old or the new one, never a mix.
  - No heuristic Konsist rule.

### 6. Mechanical confinement [CR-3b]
SQLDelight's `transaction`/`transactionWithResult` appear in `src/main` **only in `:persistenza`**. This is enforced
by Konsist (CR-3b), with the `enforced_by` script above as backup.

### 7. Blocks
- `b1-lettura-coerente-primitiva`:
  - the port, the Finta, the contract;
  - `UnitaDiLavoroSql`'s second port and the driver mode;
  - `checkpointDopoCommit()`;
  - the SQL-only tests.
- `b2-lettura-coerente-migrazione`:
  - the sites of §5, plus `ParlanteRepositorySql:84` → `checkpointDopoCommit()`;
  - the per-repository concurrency cases;
  - Konsist CR-3b and the script.

## Rejected options
- **`inLettura` as a method on `UnitaDiLavoro`** (the architect's recommendation). The user preferred a separate port,
  so read-only consumers do not see a write API. Sharing the state is solved by one class implementing both.
- **Going back to DEFERRED for writes.** It reopens `SQLITE_BUSY_SNAPSHOT` (ADR 0006 (b)). Only reads change.
- **Guarding writes-in-reads by contract and review only.** A race would surface as `SQLITE_BUSY_SNAPSHOT` in
  production. `query_only` turns it into a deterministic failure in every test.
- **A `SnastroDatabase.inLettura` entry point for adapters.** It would be a second route with a second copy of the
  per-thread state.

## Consequences
- **ADR 0012, Amendment (e):** the kernel declares two transaction ports. The nested-doom rule also covers reads
  joined into a write.
- **ADR 0006, Amendment (c):** "every **write** transaction begins `BEGIN IMMEDIATE`". Reads through `LetturaCoerente`
  begin `BEGIN DEFERRED` + `query_only`.
- **ADR 0022 §4:** `RiassuntoRepositorySql.trova` reads in a snapshot.
- **`architecture.md`:** the kernel row gains `LetturaCoerente`. "Transactions and events" gains the read rule.
- **Every implementation of `LetturaCoerente` must pass `LetturaCoerenteContratto`.** A consumer is wired only with the
  instance that also backs its `UnitaDiLavoro`.
