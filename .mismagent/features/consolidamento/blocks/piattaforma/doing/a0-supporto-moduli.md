---
id: a0-supporto-moduli
type: port
context: piattaforma
side: app
wave: 1
release: R3c
module: "settings.gradle.kts, build.gradle.kts (verificaDipendenzeModuli), ./supporto (:supporto, snastro.supporto), ./supporto-test (:supporto-test, snastro.supporto.test), :architettura-test (Konsist CR-18 + controlli-adr)"
title: "Moduli :supporto e :supporto-test (primo taglio dell'API), allowlist, Konsist CR-18, controlli ADR 0028"
consumes: []
related_adrs:
  - "0002"
  - "0028"
model_hint: deep
tests_nl_status: confirmed
---
# a0-supporto-moduli

## What to do
Create the two domain-free technical libraries of ADR 0028: :supporto (only kotlinx-coroutines-core, no project dependency) with the pinned first-cut API RitentaConBackoff, Segnalazione, gestoreErroriNonCatturati, figlioDi, catturaNonFatale, and :supporto-test (kotlin-test, junit-jupiter, kotlinx-coroutines-test) with attendiFinche, OrologioFinto, conScopeDiProva, each implemented and unit-tested. Add the allowlist rows and the test-only rule to verificaDipendenzeModuli, the Konsist rules CR-18a/b/c plus 'no snastro.supporto.test in src/main or src/testFixtures', and the two ADR 0028 scripts with red-green fixtures, then activate them in ADR 0028's enforced_by. No consumer is migrated here.

## Tasks
- AC-C1 `./gradlew check` is green with :supporto and :supporto-test in settings.gradle.kts (dirs ./supporto, ./supporto-test; packages snastro.supporto / snastro.supporto.test); both apply snastro.kotlin-jvm + snastro.explicit-api and NOT java-test-fixtures; :supporto declares only kotlinx-coroutines-core, :supporto-test only kotlin-test, junit-jupiter, kotlinx-coroutines-test; neither build file contains `project(`
- AC-C2 verificaDipendenzeModuli has the rows ":supporto" to emptySet() and ":supporto-test" to emptySet(), and :supporto in the sets of every *:adattatori and :ui. Throwaway probes (not committed) each make the task FAIL: implementation(project(":supporto")) in :kernel, in :trascrizione:dominio, in :sintesi:applicazione, in :persistenza; implementation(project(":kernel")) in :supporto
- AC-C3 Test-only rule: testImplementation(project(":supporto-test")) passes in :kernel and in :avvio; the same edge as implementation, api, testFixturesImplementation or testFixturesApi each FAILs the task, and any configuration of it in :llama-jni FAILs (probes, not committed)
- AC-C4 Konsist, one throwaway probe per rule (not committed), each makes :architettura-test FAIL and the tree passes: CR-18a a file in snastro.supporto importing snastro.kernel.Esito; CR-18b a declaration `RitentaRegistrazione` in :supporto (token list sourced once from context-map.md: at least Progetto, Registrazione, Elaborazione, Trascritto, Voce, Segmento, Parlante, Impronta, Attribuzione, Documento, Riassunto, Fonte); CR-18c an extra public `fun extra()` in :supporto, and the removal of any pinned declaration; `import snastro.supporto.test.attendiFinche` under ui/src/main and under ui/src/testFixtures
- AC-C5 RitentaConBackoff coalesces: richiedi(k) three times before the job for k starts → the job runs ONCE for k; richiedi(k1), richiedi(k2) → both run once (runTest, virtual time)
- AC-C6 RitentaConBackoff backs off: attesaIniziale 1 s, attesaMassima 4 s, a job returning false five times then true → six runs separated by 1, 2, 4, 4, 4 s of virtual time; segnala is called once per failure with a message naming the key, and once on recovery with causa = null
- AC-C7 RitentaConBackoff never drops a key silently: a job throwing IllegalStateException for k → segnala(message naming k, that exception) and k is retried; after 10 consecutive failures k is still scheduled; after scope.cancel() no further run and no segnala
- AC-C8 RitentaConBackoff rethrows fatal throwables: a job throwing an Error subclass (e.g. StackOverflowError) is NOT reported as a retry — it escapes the worker to the scope's CoroutineExceptionHandler; a CancellationException stops the worker with no segnala call. The file contains no runCatching
- AC-C9 figlioDi(genitore, dispatcher, gestore): a launch failing in the child goes to `gestore` and cancels neither the parent nor a sibling child (SupervisorJob tied to the parent's Job); cancelling the parent cancels the child; with dispatcher = null the child runs on the parent's dispatcher, otherwise on the given one
- AC-C10 gestoreErroriNonCatturati(segnala): an exception escaping a launch on a scope carrying it → segnala called exactly once with that throwable
- AC-C11 catturaNonFatale table test: a value → Result.success(value); RuntimeException and IOException → Result.failure; CancellationException, OutOfMemoryError and StackOverflowError (VirtualMachineError) are rethrown
- AC-C12 :supporto-test: attendiFinche returns as soon as the condition turns true and otherwise fails after `timeout` with an AssertionError whose message contains `messaggio`; OrologioFinto(inizio).instant() == inizio, avanza(5.seconds) moves it exactly 5 s, imposta(t) sets t, zone defaults to UTC; conScopeDiProva cancels its scope in finally even when the block throws an AssertionError (scope.isActive is false afterwards)
- AC-C13 architettura-test/controlli-adr/adr-0028-supporto-senza-progetti.sh and adr-0028-supporto-test-solo-nei-test.sh exist with red-green fixtures under fixture/<check>/ for every FAIL clause of ADR 0028 (project(/rootProject/rootDir/../ in either build file; :supporto-test in a non-test configuration incl. testFixtures*; `import snastro.supporto.test` under */src/main or */src/testFixtures, comment lines stripped), validated via ControlliAdrTest (bash -c); both exit 0 on the tree; ADR 0028's PLANNED comments are replaced by real `- check:` + `from: a0-supporto-moduli` entries and `MM lint --adrs .mismagent/decisions` is clean
- AC-C14 (by-construction) no consumer is migrated in this block: no src/main outside ./supporto references snastro.supporto — the migration is a1–a4

## Dependencies
- `supporto-api` (owns it) — consumers: `b1-lettura-coerente-primitiva`, `a1-supporto-test-adozione`, `a2-ritenta-documento`, `a3-ritenta-parlanti`, `a4-supporto-avvio`, `c3-composizione-piatta` · contract_test: consumer-driven
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
PRE-RELEASE (features/sintesi/pre-release.md): closes no line by itself — it is the enabler of lines 42, 82, 119, 171, 172, 124, 179 (closed by a1, a2, a4). The API signatures are pinned in boundary supporto-api (the ADR fixes semantics; the RitentaConBackoff constructor (lavoro, segnalazione, attesaIniziale, attesaMassima) was CONFIRMED by the user at the 2026-09-27 checkpoint — see Dependencies). CR-18c's list and ADR 0028 §2 must stay equal: adding a public declaration amends the ADR. Keep EsitoAtteso in :kernel testFixtures (ADR 0028 §3).

Sources: ADR 0028 §1–§7 + enforced_by (PLANNED), code-rules.md CR-2 note + CR-18a/b/c, dev-architecture-app.md #test + #dipendenze-test, architecture.md module map/edges, analisi-design.md §2.4 X5/X8/X11, §2.7 T1/T2, tactical-model.md (no domain change)
