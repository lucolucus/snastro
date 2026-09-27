# Gate proof — app (2026-09-27, integration/sintesi @ c6f5c41)

Gate: `./gradlew check` (run as `--no-daemon --max-workers=2 --continue`). Renewed because gate_files changed
(Q1+Q2 gate hygiene: gradle.properties, build-logic timeout, architettura-test scope/.sh scripts; llama-jni/config).

- Full run from scratch (`--rerun-tasks`): green except the known flake RitrascriviR2Test AC-459; that class
  re-run alone: green.
- **RED probe:** a `SondaGateTest` that calls `fail("sonda gate")` planted in `:kernel`, `:sintesi:dominio`
  (deepest domain module) and `:avvio` (app module). Gate → BUILD FAILED: `:kernel:test FAILED`,
  `:sintesi:dominio:test FAILED`, `:avvio:test FAILED`, each on `SondaGateTest.kt:9`.
- **GREEN rerun:** probes removed → BUILD SUCCESSFUL (2m 4s).

Note: the gate must run with no source copies under the project root: Konsist 0.17 walks the whole root to
resolve parents (`parents()`/`hasParentWithName`), so block worktrees live in `../sintesi-blocchi/`, not `.worktrees/`.
