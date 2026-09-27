---
id: b1-lettura-coerente-primitiva
type: port
context: piattaforma
side: app
wave: 2
release: R3c
module: ":kernel (LetturaCoerente.kt + testFixtures LetturaCoerenteContratto, UnitaDiLavoroFinta), :persistenza (UnitaDiLavoroSql, DriverSqliteImmediato, checkpointDopoCommit)"
title: "Porta kernel LetturaCoerente (BEGIN DEFERRED + query_only), contratto, Finta, UnitaDiLavoroSql su due porte, checkpointDopoCommit"
consumes:
  - supporto-api
related_adrs:
  - "0003"
  - "0006"
  - "0012"
  - "0029"
model_hint: deep
tests_nl_status: confirmed
---
# b1-lettura-coerente-primitiva

## What to do
Add the kernel port LetturaCoerente (inLettura returns T, infra faults throw) and the separate abstract LetturaCoerenteContratto (cases 1–7 of ADR 0029 §4) in :kernel testFixtures, with UnitaDiLavoroFinta implementing both ports on one depth counter and one mode. In :persistenza make UnitaDiLavoroSql implement UnitaDiLavoro and LetturaCoerente on its one per-thread state, give DriverSqliteImmediato a per-thread begin mode (IMMEDIATE by default, DEFERRED + PRAGMA query_only = 1 only for the outermost read, always reset), add checkpointDopoCommit(), and prove snapshot (8) and no-queueing (9) on SQL. UnitaDiLavoro and UnitaDiLavoroContratto stay unchanged; no consumer is migrated here.

## Tasks
- AC-C15 LetturaCoerenteContratto case 1 — inLettura returns the block's value and sees committed state — passes on its three subclasses: the Finta's test (:kernel), UnitaDiLavoroSql, and UnitaDiLavoroSql behind DispatcherEventiInMemoria.unitaDiLavoro (:persistenza); the Ambiente exposes lettura and unitaDiLavoro backed by the same state, scrivi(effetto), effetti(), leggi()
- AC-C16 Case 2: inLettura nested in inTransazione joins it and sees the transaction's uncommitted write; on SQL no second BEGIN is issued and no pragma is set
- AC-C17 Case 3: inLettura nested in inLettura joins (one read transaction; the inner sees what the outer sees)
- AC-C18 Case 4: inTransazione called inside inLettura throws IllegalStateException BEFORE running its block (block never entered, effetti() unchanged)
- AC-C19 Case 5: a write inside inLettura fails and leaves no effect — on SQL it surfaces SQLITE_READONLY from query_only; the Finta refuses scrivi while letturaAperta
- AC-C20 Case 6 (no leak): an exception thrown in inLettura propagates to the caller; the next outermost inTransazione on the same thread commits normally, its write is readable afterwards and it is NOT read-only (query_only back to 0, mode back to none; on SQL it begins BEGIN IMMEDIATE)
- AC-C21 Case 7: an exception in an inLettura nested in inTransazione dooms the whole unit — no effect is committed even when the outer block catches the exception and returns Ok
- AC-C22 UnitaDiLavoro.kt and UnitaDiLavoroContratto are byte-unchanged and every existing UnitaDiLavoroContratto subclass stays green (git diff of both files is empty)
- AC-C23 SQL-only case 8 (snapshot): inside ONE inLettura on thread A, SELECT 1 runs, then thread B commits a write (latch-driven), then SELECT 2 on A does NOT see B's write; a new inLettura after A ends does
- AC-C24 SQL-only case 9 (no queueing): while thread B holds an open BEGIN IMMEDIATE (latched, uncommitted), inLettura on thread A completes and returns the committed state within 1 s — far below the 5 s busy_timeout, no SQLITE_BUSY; latch-driven via :supporto-test attendiFinche, no Thread.sleep
- AC-C25 DriverSqliteImmediato keeps writes byte-identical: the outermost inTransazione still issues BEGIN IMMEDIATE (asserted on the issued statement), the existing ADR 0006 (b) tests stay green; a fault-injected failing BEGIN DEFERRED leaves the per-thread mode reset and the transaction slot cleared, so the next inTransazione on that thread begins BEGIN IMMEDIATE
- AC-C26 checkpointDopoCommit(): called inside an inTransazione that commits → PRAGMA wal_checkpoint(TRUNCATE) runs exactly once, after the commit; inside one that rolls back → it never runs; two calls in one transaction → two checkpoints (no dedup, user Q-1 2026-09-25)
- AC-C27 UnitaDiLavoroFinta is ONE class implementing UnitaDiLavoro and LetturaCoerente: letturaAperta is true only inside the outermost inLettura, transazioneAperta keeps its current meaning, and the existing Finta tests stay green

## Dependencies
- `lettura-coerente` (owns it) — consumers: `b2-lettura-coerente-migrazione`, `c1-porte-progetto`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `LetturaCoerente (:kernel, snastro.kernel)`: public interface LetturaCoerente { public fun <T> inLettura(blocco: () -> T): T } — ONE consistent snapshot, read-only; infra faults throw (ADR 0003), no Esito
  - pinned `UnitaDiLavoro (:kernel, unchanged)`: public interface UnitaDiLavoro { public fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> } — signature and UnitaDiLavoroContratto unchanged
  - pinned `semantics (ADR 0029 §2, binding)`: one per-thread state shared by both ports (depth, mode none|write|read, doom flags): 1 outermost inLettura → BEGIN DEFERRED + PRAGMA query_only = 1; 2 inLettura in inTransazione joins, sees uncommitted writes; 3 inLettura in inLettura joins; 4 inTransazione in inLettura → IllegalStateException before any effect; 5 a write in a read fails hard (SQLITE_READONLY / Finta refuses); 6 an exception in a nested inLettura dooms the enclosing inTransazione; 7 no leak: query_only 0 and mode none before the connection goes back
  - pinned `UnitaDiLavoroSql (:persistenza)`: public class UnitaDiLavoroSql(db: SnastroDatabase) : UnitaDiLavoro, LetturaCoerente — its ThreadLocal<Stato> gains the mode; the composition passes the SAME instance as the project's LetturaCoerente and as DispatcherEventiInMemoria's delegate; the dispatcher's wrapper does not wrap LetturaCoerente
  - pinned `DriverSqliteImmediato (:persistenza)`: per-thread begin mode, IMMEDIATE by default (writes byte-identical), DEFERRED only for the outermost read set by UnitaDiLavoroSql; the slot-clearing on a failed BEGIN applies to both modes
  - pinned `checkpointDopoCommit (:persistenza, snastro.persistenza)`: public fun SnastroDatabase.checkpointDopoCommit() — registers PRAGMA wal_checkpoint(TRUNCATE) after the current transaction commits (joins the caller's transaction, as ParlanteRepositorySql:84 does today); dropped on rollback; one checkpoint per call
  - pinned `LetturaCoerenteContratto (:kernel testFixtures)`: abstract class; Ambiente { lettura: LetturaCoerente; unitaDiLavoro: UnitaDiLavoro /* same state */; scrivi(effetto: String); effetti(): List<String>; leggi(): Set<String> /* through lettura */ }; cases 1–7 of ADR 0029 §4; subclasses: Finta (:kernel), UnitaDiLavoroSql and UnitaDiLavoroSql behind DispatcherEventiInMemoria.unitaDiLavoro (:persistenza)
  - pinned `UnitaDiLavoroFinta (:kernel testFixtures)`: ONE class implementing UnitaDiLavoro and LetturaCoerente (one depth counter, one mode); val transazioneAperta (unchanged), val letturaAperta; refuses scrivi while a read is open
- `supporto-api` (consumes it; owner `a0-supporto-moduli`) — consumers: `b1-lettura-coerente-primitiva`, `a1-supporto-test-adozione`, `a2-ritenta-documento`, `a3-ritenta-parlanti`, `a4-supporto-avvio`, `c3-composizione-piatta` · contract_test: consumer-driven
  - pinned `Segnalazione (snastro.supporto)`: public fun interface Segnalazione { public fun segnala(messaggio: String, causa: Throwable?) } — the ONLY logging channel; :supporto never touches JUL; :avvio injects the JUL-backed implementation
  - pinned `RitentaConBackoff (snastro.supporto)`: public class RitentaConBackoff<K>(lavoro: suspend (K) -> Boolean /* true = done, false = retry */, segnalazione: Segnalazione, attesaIniziale: Duration, attesaMassima: Duration) { public fun avvia(scope: CoroutineScope): Job; public fun richiedi(chiave: K) } — keys coalesce over a conflated signal; bounded exponential backoff (doubling from attesaIniziale, capped at attesaMassima); segnala on every failure (key + cause, cause null for a false return) and once on recovery; a failed key is retried until success or scope cancel; rethrows CancellationException and every Error, never runCatching around them (constructor shape CONFIRMED by the user, checkpoint 2026-09-27)
  - pinned `gestoreErroriNonCatturati (snastro.supporto)`: public fun gestoreErroriNonCatturati(segnala: Segnalazione): CoroutineExceptionHandler
  - pinned `figlioDi (snastro.supporto)`: public fun figlioDi(genitore: CoroutineScope, dispatcher: CoroutineDispatcher? = null, gestore: CoroutineExceptionHandler): CoroutineScope — child scope with a SupervisorJob tied to the parent's Job
  - pinned `catturaNonFatale (snastro.supporto)`: public inline fun <T> catturaNonFatale(blocco: () -> T): Result<T> — rethrows CancellationException and VirtualMachineError, catches everything else; the one sanctioned catch-all at the edges (CR-7)
  - pinned `attendiFinche (snastro.supporto.test)`: public fun attendiFinche(timeout: Duration = 5.seconds, messaggio: String, condizione: () -> Boolean) — polls with a short sleep, fails with AssertionError(messaggio)
  - pinned `OrologioFinto (snastro.supporto.test)`: public class OrologioFinto(inizio: Instant, zona: ZoneId = ZoneOffset.UTC) : java.time.Clock { public fun avanza(durata: Duration); public fun imposta(istante: Instant) }
  - pinned `conScopeDiProva (snastro.supporto.test)`: public fun <T> conScopeDiProva(blocco: (CoroutineScope) -> T): T — runs the block and cancels the scope in finally; plus StandardTestDispatcher helpers for runTest + backgroundScope
  - pinned `excluded (ADR 0028 §4)`: no context type, id, Esito, ErroreDominio or business rule; no claim/run/complete template; EsitoAtteso stays in :kernel testFixtures
  - key `K (RitentaConBackoff key)`: minted by the CONSUMER block, never by :supporto — a2: the Registrazione id as String (RegistrazioneId.valore, UUID v4 minted by AggiungiRegistrazione, immutable); a3: the key AbbonatoRiallineamentoImpronte coalesces on today, unchanged; coalescing uses equals()

## Notes
PRE-RELEASE (features/sintesi/pre-release.md): closes line 175 ("a thrown trova marks the caller transaction failed") by DECISION, not by code: ADR 0029 §2 rule 6 [user B3] makes the doom the contract and AC-C21 pins it — tick it `[x] … · decided by ADR 0029 §2.6, pinned by AC-C21`. Line 170 is closed by b2 (the primitive alone does not change TrascrittoRepositorySql). The event-publishing wrapper does NOT wrap LetturaCoerente (ADR 0029 §3). There is no SnastroDatabase.inLettura entry point. SQL-only tests use the :supporto-test helpers (hence consumes supporto-api).

Sources: ADR 0029 §1–§4, §7 + Consequences (ADR 0012 amendment (e), ADR 0006 amendment (c)), code-rules.md CR-3b, dev-architecture-app.md #repository + #porta-contratto, analisi-design.md §2.4 X1, tactical-model.md (no domain change)
