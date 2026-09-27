---
id: b2-lettura-coerente-migrazione
type: adapter
context: piattaforma
side: app
wave: 3
release: R3c
module: ":trascrizione:adattatori (TrascrittoRepositorySql), :sintesi:adattatori (RiassuntoRepositorySql) + the Sintesi read side of the riassunto tab, :parlanti:adattatori (ParlanteRepositorySql), :parlanti:applicazione (RiallineaTutteLeImpronteServizio), :avvio (wiring of LetturaCoerente, EstensioneR3 read removed), :architettura-test (Konsist CR-3b + controlli-adr)"
title: "Migrazione a LetturaCoerente: trova multi-tabella in snapshot, lettura della scheda riassunto, RiallineaTutteLeImpronte, checkpointDopoCommit, CR-3b"
consumes:
  - lettura-coerente
related_adrs:
  - "0006"
  - "0012"
  - "0020"
  - "0022"
  - "0029"
tests_nl_status: confirmed
---
# b2-lettura-coerente-migrazione

## What to do
Apply ADR 0029 §5–§6: TrascrittoRepositorySql.trova, RiassuntoRepositorySql.trova and ParlanteRepositorySql.trova read root and children inside lettura.inLettura, inside the repository (LetturaCoerente as a constructor parameter); the riassunto tab read leaves EstensioneR3:151 for the Sintesi read side; RiallineaTutteLeImpronteServizio reads through LetturaCoerente; ParlanteRepositorySql:84 uses checkpointDopoCommit(). Add one concurrency case per multi-table repository's SQL Contratto subclass, Konsist CR-3b and the ADR 0029 script, and activate it in the ADR.

## Tasks
- AC-C28 TrascrittoRepositorySql.trova has no transactionWithResult: called outside any unit of work it reads in a DEFERRED snapshot — while another thread holds an uncommitted BEGIN IMMEDIATE, trova returns the last committed Trascritto within 1 s with no SQLITE_BUSY (latch-driven)
- AC-C29 Called inside a command's inTransazione, TrascrittoRepositorySql.trova joins it and sees its uncommitted writes; a trova that throws there dooms the unit (ADR 0029 §2 rule 6), asserted end to end on SQL
- AC-C30 RiassuntoRepositorySql.trova and ParlanteRepositorySql.trova read the root row and their child rows inside ONE lettura.inLettura (no read of the root outside the snapshot)
- AC-C31 Concurrency case in the SQL subclass of TrascrittoRepositoryContratto, RiassuntoRepositoryContratto and ParlanteRepositoryContratto: a test SqlDriver decorator parks the reader right after the root SELECT while a writer on another thread commits a rewrite with a different number of children; the read returns exactly the OLD aggregate (never root-new/children-old or the reverse), and a fresh read returns exactly the NEW one. A throwaway probe removing the inLettura wrap makes each case FAIL (not committed)
- AC-C32 The riassunto tab read no longer lives in EstensioneR3: it is in the Sintesi read side, which receives LetturaCoerente and reads segmenti and riassunti in one inLettura; EstensioneR3 has no inTransazione around a read and no `(… as Esito.Ok).valore` unwrap there; the presenter and segnoRiassunto still read the same view (existing AC-S104..S108 and ComposizioneR3Test green)
- AC-C33 RiallineaTutteLeImpronteServizio takes a LetturaCoerente for its reads and calls no inTransazione to read; its existing ACs stay green
- AC-C34 ParlanteRepositorySql has no `db.transaction {` — rimuovi calls checkpointDopoCommit(); AC-634 (exactly 3 after-commit wal_checkpoint end to end) stays green
- AC-C35 In the current composition the LetturaCoerente handed to each migrated repository and read side is the SAME instance (===) as the UnitaDiLavoroSql the project's DispatcherEventiInMemoria delegates to (identity asserted in the existing composition test Ambiente)
- AC-C36 TrascrittoRepositorySqlLetturaAtomicaTest no longer waits a fixed 1 s nor depends on IMMEDIATE timing: it is latch-driven with attendiFinche and still fails if trova reads outside a snapshot (probe, not committed)
- AC-C37 Konsist CR-3b: a throwaway `db.transaction { }` in a *:adattatori src/main file and one in :avvio src/main each FAIL the rule; the tree passes (only :persistenza calls SQLDelight's transaction/transactionWithResult)
- AC-C38 architettura-test/controlli-adr/adr-0029-transazioni-solo-in-persistenza.sh exists with red-green fixtures (a transaction {, transaction( and transactionWithResult call outside persistenza/ each RED; the same inside persistenza/ and in a comment line GREEN), validated via ControlliAdrTest; exits 0 on the tree; ADR 0029's PLANNED comment becomes a real `- check:` + `from: b2-lettura-coerente-migrazione` entry

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
PRE-RELEASE (features/sintesi/pre-release.md): closes line 170 (TrascrittoRepositorySql:37 BEGIN IMMEDIATE on reads — AC-C28), line 176 (EstensioneR3 riassunto-vista read — AC-C32), line 92 (riassunto-vista reads not one snapshot — AC-C32). Line 172 is PARTIAL here (its second half: the atomicity test's 1 s wait — AC-C36); a2 completes and ticks it. The concurrency cases are the enforcement of ADR 0029 §5 (no heuristic Konsist rule). Read-models and services that only read receive LetturaCoerente, never UnitaDiLavoro (dev-architecture #repository).

Sources: ADR 0029 §3, §5, §6, §7 + enforced_by (PLANNED), ADR 0022 §4, ADR 0020 (checkpoint obligation), code-rules.md CR-3b, dev-architecture-app.md #repository, analisi-design.md §2.4 X1/X2, tactical-model.md (no domain change)
