---
id: a1-supporto-test-adozione
type: adapter
context: piattaforma
side: app
wave: 2
release: R3c
module: "test source sets only (src/test) of :avvio, :ui, :audio, :progetto:adattatori, :sintesi:adattatori, :modelli (+ testImplementation(project(\":supporto-test\")) in their build files)"
title: "Adozione di :supporto-test nei test: un solo attendiFinche, OrologioFinto al posto degli sleep, scope cancellati in finally; flake noti deterministici"
consumes:
  - supporto-api
related_adrs:
  - "0028"
tests_nl_status: draft
---
# a1-supporto-test-adozione

## What to do
Replace every private attendi/attendiFinche copy, every Thread.sleep used to wait or to separate instants, and every hand-cancelled test scope in the test source sets of :avvio (first: most copies and the flakes), :ui, :audio, :progetto, :sintesi and :modelli with :supporto-test's attendiFinche, OrologioFinto and conScopeDiProva/backgroundScope. The known flakes RitrascriviR2Test AC-458/459/479, RegistrazioneAttesaTest AC-417 and RegistrazioneSomiglianzaTest AC-536 become deterministic, and :modelli's concurrency tests get a deterministic start barrier. Test source sets only; testFixtures copies stay until M2/S5 (ADR 0028 §5).

## Tasks
- AC-C39 No private polling helper remains in the test source sets of :avvio, :ui, :audio, :progetto, :sintesi and :modelli: `git grep -nE 'fun (attendi|attendiFinche|aspetta)\b' -- '*/src/test/**'` in those modules returns nothing; every wait calls snastro.supporto.test.attendiFinche
- AC-C40 No Thread.sleep separates instants: ComposizioneR3Test (AC-S146 richiestoAlle) and every other test in those modules that slept to make two timestamps differ use OrologioFinto.avanza; each Thread.sleep left in those test source sets carries a one-line comment stating why real time is the subject of the test (listed in the block hand-off)
- AC-C41 Every scope a test in those modules creates is cancelled in finally (conScopeDiProva or runTest's backgroundScope): CodaCondivisaTest and CodaCondivisaMultiSorgenteTest have no `scope.cancel()` after an assertion; a throwaway wrong-order assertion makes such a test FAIL (not hang) within the default JUnit timeout (probe, not committed)
- AC-C42 RitrascriviR2Test AC-458/AC-459/AC-479 wait on a condition with attendiFinche (no fixed sleep followed by one comparison) and pass 20 consecutive runs of `./gradlew :avvio:test --tests '*RitrascriviR2Test' --rerun-tasks` (evidence attached to the hand-off)
- AC-C43 RegistrazioneAttesaTest AC-417 and RegistrazioneSomiglianzaTest AC-536 wait with attendiFinche/virtual time (no budget loop of sleeps) and pass 20 consecutive runs of `./gradlew :ui:test --tests '*RegistrazioneAttesaTest' --tests '*RegistrazioneSomiglianzaTest' --rerun-tasks` with -Dkotlinx.coroutines.debug=on (evidence attached)
- AC-C44 The block's diff touches no */src/main and no */src/testFixtures file; :supporto-test is added ONLY as testImplementation (verificaDipendenzeModuli and ADR 0028 script 2 green)
- AC-C86 :modelli concurrency tests start deterministically: ProvisioningModelliTest 'chiamate concorrenti a scarica sono serializzate' (and the other N-thread cases of that file) release all callers from one start barrier (CountDownLatch/CyclicBarrier awaited before the first call, completion waited with attendiFinche, no Thread.sleep), and ServerLocaleDiProva's contatoreRichieste is thread-safe (ConcurrentHashMap.merge / AtomicInteger); a throwaway probe that removes the serialization lock in ProvisioningModelli makes the concurrency test FAIL on every one of 20 consecutive runs (not committed)

## Dependencies
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
ALREADY SATISFIED before this block (not work): the default JUnit timeout and the thread-name assertion of RegistrazioneAttesaTest AC-417 landed in quick-win Q1 (386d57b, merged 54f125e) — ADR 0028 §7.2's 'a default JUnit timeout lands here too' is therefore context. PRE-RELEASE (features/sintesi/pre-release.md): closes line 42 (FLAKE AC-417 thread name — fixed by Q1, ticked here once AC-C43's 20 runs are green, cite 386d57b), line 82 (FLAKE AC-479 — AC-C42), line 119 (queue tests hang: scope.cancel after the assertion — AC-C41; the timeout half was Q1). Line 179 is PARTIAL here (AC-S146 Thread.sleep(20) — AC-C40; its AC-S150 hang half was Q1); c3 completes and ticks it (AC-S143 reflection). Closes lines 50 (pre-existing bulk concurrency test in ProvisioningModelliTest has no start barrier) and 51 (ServerLocaleDiProva contatoreRichieste not thread-safe) — AC-C86 (user checkpoint 2026-09-27 added :modelli to the scope). May run alongside b1/b2 (only after a0).

Sources: ADR 0028 §3, §5, §7.2, dev-architecture-app.md #test + #dipendenze-test, code-rules.md CR-18 (test-only edge), analisi-design.md §2.7 T1/T2/T3, tactical-model.md (no domain change)
