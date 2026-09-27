---
id: c1-porte-progetto
type: adapter
context: piattaforma
side: app
wave: 3
release: R3c
module: ":avvio (new PorteProgetto built by SessioneProgettoImpl; EstensioneR1/R2/R3, PorteParlanti, puliziaDerivati consume it)"
title: "PorteProgetto costruito una volta per progetto, dentro la struttura R attuale"
consumes:
  - lettura-coerente
related_adrs:
  - "0012"
  - "0029"
  - "0030"
tests_nl_status: draft
---
# c1-porte-progetto

## What to do
Introduce PorteProgetto, built once per opened project by SessioneProgettoImpl: the database, ONE UnitaDiLavoroSql used as both UnitaDiLavoro and LetturaCoerente, the DispatcherEventiInMemoria over it, one instance of each SQL repository, CatalogoRegistrazioni and the cross-context readers. The R1/R2/R3 extensions take their collaborators from it instead of building their own, which removes the duplicate instances and R3's second StatiElaborazione over a fake FasiInCorso. The R-structure itself is unchanged.

## Tasks
- AC-C60 Per project open, each SQL repository constructor (TrascrittoRepositorySql, ParlanteRepositorySql, AttribuzioneRepositorySql, RiassuntoRepositorySql, …) and CatalogoRegistrazioni runs exactly ONCE (counting probe in the composition test Ambiente; TrascrittoRepositorySql is built 5 times today), and `RepositorySql(` appears in avvio/src/main only in PorteProgetto
- AC-C61 Every consumer in R1, R2 and R3 receives the identical (===) instance of each repository, of CatalogoRegistrazioni and of RigenerazioneDocumentoPolitica
- AC-C62 The LetturaCoerente of every read-only consumer and the delegate of the project's DispatcherEventiInMemoria are the same UnitaDiLavoroSql instance (===)
- AC-C63 R3 no longer builds a second StatiElaborazione with a fake FasiInCorso(): during a running Elaborazione the phase seen by S2 and by the Sintesi side is the same (one StatiElaborazione)
- AC-C64 The R0–R3 structure and every existing :avvio test stay green, with no test deleted or renamed in this block

## Dependencies
- `lettura-coerente` (consumes it; owner `b1-lettura-coerente-primitiva`) — consumers: `b2-lettura-coerente-migrazione`, `c1-porte-progetto`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `LetturaCoerente (:kernel, snastro.kernel)`: public interface LetturaCoerente { public fun <T> inLettura(blocco: () -> T): T } — ONE consistent snapshot, read-only; infra faults throw (ADR 0003), no Esito
  - pinned `UnitaDiLavoro (:kernel, unchanged)`: public interface UnitaDiLavoro { public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — signature and UnitaDiLavoroContratto unchanged
  - pinned `semantics (ADR 0029 §2, binding)`: one per-thread state shared by both ports (depth, mode none|write|read, doom flags): 1 outermost inLettura → BEGIN DEFERRED + PRAGMA query_only = 1; 2 inLettura in inTransazione joins, sees uncommitted writes; 3 inLettura in inLettura joins; 4 inTransazione in inLettura → IllegalStateException before any effect; 5 a write in a read fails hard (SQLITE_READONLY / Finta refuses); 6 an exception in a nested inLettura dooms the enclosing inTransazione; 7 no leak: query_only 0 and mode none before the connection goes back
  - pinned `UnitaDiLavoroSql (:persistenza)`: public class UnitaDiLavoroSql(db: SnastroDatabase) : UnitaDiLavoro, LetturaCoerente — its ThreadLocal<Stato> gains the mode; the composition passes the SAME instance as the project's LetturaCoerente and as DispatcherEventiInMemoria's delegate; the dispatcher's wrapper does not wrap LetturaCoerente
  - pinned `DriverSqliteImmediato (:persistenza)`: per-thread begin mode, IMMEDIATE by default (writes byte-identical), DEFERRED only for the outermost read set by UnitaDiLavoroSql; the slot-clearing on a failed BEGIN applies to both modes
  - pinned `checkpointDopoCommit (:persistenza, snastro.persistenza)`: public fun SnastroDatabase.checkpointDopoCommit() — registers PRAGMA wal_checkpoint(TRUNCATE) after the current transaction commits (joins the caller's transaction, as ParlanteRepositorySql:84 does today); dropped on rollback; one checkpoint per call
  - pinned `LetturaCoerenteContratto (:kernel testFixtures)`: abstract class; Ambiente { lettura: LetturaCoerente; unitaDiLavoro: UnitaDiLavoro /* same state */; scrivi(effetto: String); effetti(): List<String>; leggi(): Set<String> /* through lettura */ }; cases 1–7 of ADR 0029 §4; subclasses: Finta (:kernel), UnitaDiLavoroSql and UnitaDiLavoroSql behind DispatcherEventiInMemoria.unitaDiLavoro (:persistenza)
  - pinned `UnitaDiLavoroFinta (:kernel testFixtures)`: ONE class implementing UnitaDiLavoro and LetturaCoerente (one depth counter, one mode); val transazioneAperta (unchanged), val letturaAperta; refuses scrivi while a read is open

## Notes
PRE-RELEASE (features/sintesi/pre-release.md): closes no line directly (duplicate per-project instances are analysis R2 evidence, not pre-release findings); it enables 56/143 (graph-level duplicates) closed by c3. LayoutCartellaProgetto (ADR 0030 §1) joins PorteProgetto only if quick-win Q5 has landed; otherwise it is added when Q5 does.

Sources: ADR 0030 §1, §4 (c1), ADR 0029 §3 (one instance for both ports), dev-architecture-app.md §10 #composizione, analisi-design.md §2.3 R2, tactical-model.md (no domain change)
