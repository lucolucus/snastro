# Acceptance tests

**Rule.** Acceptance tests live under *tests/acceptance/src/test/kotlin* (package `snastro.avvio.accettazione`),
compile as `:avvio` tests through an extra test source directory in `avvio/build.gradle.kts`
(`kotlin.sourceSets.named("test") { kotlin.srcDir(rootProject.file("tests/acceptance/src/test/kotlin")) }`), and name
their `EX-<n>` in the test name. They drive the screen's presenter through the real `:avvio` adapters the example
touches, say in the KDoc which boundaries are substituted (e.g. the per-user registry, ML/audio via
`componentiDiProva`), use `runTest`/`StandardTestDispatcher` with scheduled work advanced explicitly and the scope
cancelled in teardown — never a blanket `Dispatchers.Unconfined` (the coroutine-test rule of
`.mismagent/architetture/dev-architecture-app.md`). Each screen pairs them with a thin Compose test in `:ui` that
checks its controls call the right callbacks (it does not prove the presenter wiring: the acceptance test does).

**Wiring and scheduling** (added 2026-10-08, decided: codex on delegation):
- register the acceptance directory with both `kotlin.srcDir(...)` and `detekt { source.from(...) }` in `avvio/build.gradle.kts`;
- use `CoroutineScope(SupervisorJob() + dispatcher)` for the presenter, sharing a `StandardTestDispatcher` with
  `runTest(dispatcher)`; advance explicitly and cancel in `@AfterEach`;
- do not use `backgroundScope` for a presenter driven by `advanceUntilIdle()`: advancement stops when only background
  work remains queued;
- for the session's own scope, use `conScopeDiProva` when real threads are needed, or `backgroundScope` with explicit
  scheduler driving; both guarantee cancellation;
- a separate session lifetime alone does not require a real dispatcher; do not generalize an implicit `Dispatchers.Default`.
- when a real session needs real threads under the rules above, reuse `conSessione(registro, dispatcher) { sessione -> … }`
  (`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/ConSessione.kt`); it delegates to
  `conSessioneDiProva(registro) { … }` (`avvio/src/test/kotlin/snastro/avvio/progetto/SessioneDiProva.kt`), reused directly
  for unit tests and a second window.

**Another window** (added 2026-10-08, decided: codex on delegation):
- model it with a second real `SessioneProgettoImpl` that `apri`s the project and closes in teardown, with its own
  `RegistroProgettiFinta` so its asynchronous registration cannot race the list;
- this covers same-JVM lock contention (`OverlappingFileLockException` normalized in `acquisisciLock`); cross-process
  locking needs a separate-process test;
- for "nothing deleted", compare recursive relative paths, excluding only `progetto.db-wal`/`progetto.db-shm`, and
  tolerate their disappearance during traversal.

**Shared harness** (added 2026-10-08, decided: codex on delegation; lands with slice 15): S1/shell acceptance tests share
one harness beside `ConSessione.kt` — registry, dispatcher, presenter scope cancelled after each test, presenter builders,
`dati`, `elimina` — keeping the scheduling rules above; one recursive relative-path `contenuto`, excluding only
`progetto.db-wal`/`progetto.db-shm` and tolerating their disappearance during traversal; no per-file copies.

**Why.** mm looks for `EX-<n>` markers only under *tests/acceptance/*; *tests/acceptance/* as its own Gradle module
would need settings, module-map and allowed-edge changes and test fixtures out of `:avvio`, while an extra source
directory of `:avvio` keeps access to its internal test helpers. Konsist already scans `**/src/**/*.kt`.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by** (moved here by the refactor slice 06):
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`,
`ui/src/test/kotlin/snastro/ui/progetti/SchermataProgettiEliminaTest.kt`,
`avvio/src/test/kotlin/snastro/avvio/progetto/ComponentiDiProva.kt`, `avvio/build.gradle.kts`.

not: an index file under *tests/acceptance/* pointing elsewhere (markers would go stale unchecked).
