# Acceptance tests

**Rule.** Acceptance tests live in *tests/acceptance/src/test/kotlin* (package `snastro.avvio.accettazione`), registered
in `avvio/build.gradle.kts` as an extra `:avvio` test source with both `kotlin.srcDir(...)` and `detekt { source.from(...) }`.
They name their `EX-<n>`, drive the screen's presenter through the real `:avvio` adapters the example touches, and say
in the KDoc which boundaries are substituted (e.g. ML/audio via `componentiDiProva`). Each screen pairs them with a thin
Compose test in `:ui` that checks its controls call the right callbacks.

**Scheduling** (rule of `.mismagent/architetture/dev-architecture-app.md`): the presenter runs on
`CoroutineScope(SupervisorJob() + dispatcher)` sharing a `StandardTestDispatcher` with `runTest(dispatcher)`, advanced
explicitly and cancelled in `@AfterEach`. Never a blanket `Dispatchers.Unconfined`; no `backgroundScope` for a
presenter driven by `advanceUntilIdle()`. The session's own scope uses `conScopeDiProva` when real threads are
needed, else `backgroundScope` driven explicitly; a real session reuses `conSessione(registro, dispatcher) { … }` (it
delegates to `conSessioneDiProva`). A separate session lifetime alone needs no real dispatcher; never an implicit
`Dispatchers.Default`.

**Another window**: a second real `SessioneProgettoImpl` with its own `RegistroProgettiFinta`, closed in teardown;
it covers same-JVM locks only (cross-process needs a separate process). "Nothing deleted" compares recursive relative
paths, excluding only `progetto.db-wal`/`progetto.db-shm` and tolerating their disappearance during traversal.

**Shared benches**: S1/shell tests extend `BancoS1`, with one `contenuto` and no per-file copies.

**Riassunto** (2026-10-10, decided by the user): S3 Riassunto tests extend `BancoRiassunto`, which uses the real
composition and the real `ModelloLinguisticoLlama`, with only `:llama-jni` faked. The queue worker is a real thread, so
these tests wait with `attendiFinche`, not `StandardTestDispatcher`. The gate proves the shape. The real model's content
is measured with `benchmarkRiassunto -Pargomento` and noted in the spike. Test names state the example, never data the
test lacks. A run's duration is asserted as the seconds the fake library computes at the measured speed
(`TOKEN_AL_SECONDO`, cited, kept at or below the measured rate), never with real sleeps; `riassumiFinche` waits for
any tab state, `riassumi` for a shown Riassunto.

**Why.** mm finds `EX-<n>` only under *tests/acceptance/*. A source directory of `:avvio` keeps its test helpers without
a new module. Decided: codex (delegated by the user), 2026-10-08; the user, 2026-10-10.

**Shown by:** `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`,
`ui/src/test/kotlin/snastro/ui/progetti/SchermataProgettiEliminaTest.kt`, `avvio/src/test/kotlin/snastro/avvio/progetto/ComponentiDiProva.kt`,
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/ConSessione.kt`, `avvio/src/test/kotlin/snastro/avvio/progetto/SessioneDiProva.kt`,
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/BancoS1.kt`, `tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/BancoRiassunto.kt`,
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/RiassuntoSoloSommarioTest.kt`,
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/RiassuntoCicloFermatoTest.kt`, `avvio/build.gradle.kts`.

not: an index file under *tests/acceptance/* pointing elsewhere (markers would go stale unchecked).
