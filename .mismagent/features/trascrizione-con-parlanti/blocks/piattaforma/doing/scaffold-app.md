---
id: "scaffold-app"
type: "scaffold"
context: "piattaforma"
side: "app"
wave: 0
module: "root + every module of architecture.md"
consumes: []
depends_on: []
related_adrs:
  - "0001"
  - "0002"
  - "0004"
  - "0005"
  - "0006"
  - "0008"
  - "0011"
---
# scaffold-app — Scheletro Gradle multi-modulo eseguibile

## What to do
Create the buildable empty skeleton: Gradle wrapper, Kotlin DSL, gradle/libs.versions.toml (dev-architecture §9 entries), foojay + JBR 21 toolchain, build-logic convention plugin (allWarningsAsErrors, explicitApi on :kernel and *:applicazione, java-test-fixtures), all modules of architecture.md with src/{main,test,testFixtures}/kotlin, root task verificaDipendenzeModuli (edges table), :architettura-test with the Konsist rules CR-1..CR-5, CR-8, CR-10, CR-14..CR-17 (vacuously green), detekt + formatting with config/detekt/detekt.yml and no baseline, SQLDelight plugin on :persistenza (empty schema, verifySqlDelightMigration wired), :ui:renderCheck (Compose ui-test desktop, a placeholder composable rendered at 1280x800 and 1024x640, PNGs to ui/build/render-check/), JUnit tag 'modelli' excluded from check, opt-in tasks modelliTest and benchmarkElaborazione -Pcampione (no-op stubs), native fetch task skeleton scaricaNativiSherpa (coordinates/SHA read from the catalog, filled later by ml-sherpa-motore; not in check), the run binding ./gradlew :avvio:run (empty window) and --smoke <dir> (one screenshot to avvio/build/smoke/, exit 0), .gitignore un-ignoring persistenza/src/main/sqldelight/databases/.

## Tasks
- `./gradlew check` is GREEN on the empty skeleton (no domain code, no ACs, no contract test)
- `./gradlew :avvio:run --args="--smoke build/smoke-fixture"` exits 0 and writes one PNG to avvio/build/smoke/
- `./gradlew :ui:renderCheck` renders the placeholder at both sizes (part of check)
- verificaDipendenzeModuli fails on an edge absent from architecture.md (proved by a throwaway negative check during the block, not committed)
- modelliTest / benchmarkElaborazione / scaricaNativiSherpa exist and are NOT part of check

## Dependencies
- none (wave-0 scaffold: builds first)

Sources: ADRs 0001, 0002, 0004, 0005, 0006, 0008, 0011 (.mismagent/decisions/); architecture.md (module map, edges), code-rules.md (channels), infra-notes.md (Needs → work), profile (gate, ui_render_check, run).
