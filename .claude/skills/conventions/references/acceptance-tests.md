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

**Why.** mm looks for `EX-<n>` markers only under *tests/acceptance/*; *tests/acceptance/* as its own Gradle module
would need settings, module-map and allowed-edge changes and test fixtures out of `:avvio`, while an extra source
directory of `:avvio` keeps access to its internal test helpers. Konsist already scans `**/src/**/*.kt`.
Decided: codex (delegated by the user), 2026-10-08.

**Shown by** (moved here by the refactor slice 06):
`tests/acceptance/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`,
`ui/src/test/kotlin/snastro/ui/progetti/SchermataProgettiEliminaTest.kt`,
`avvio/src/test/kotlin/snastro/avvio/progetto/ComponentiDiProva.kt`, `avvio/build.gradle.kts`.

not: an index file under *tests/acceptance/* pointing elsewhere (markers would go stale unchecked).
