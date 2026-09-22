# snastro — Project profile

## Bootstrap (prerequisite of explore)

```yaml
output_dir: .mismagent
ubiquitous_language:
  lang: it                      # canonical names in Italian (the user's domain language)
validation_mode: normal         # greenfield: no prior implementation exists
materials:
  sample: sample/               # the user HAS real recordings (IT + EN); to be placed in sample/
  ui: none
capacity: "full-agentic — Claude builds, the user reviews. Dev machine: Apple M3 Pro, 36 GB RAM (Ollama installed; JDK 21 + Gradle present; ffmpeg not needed — ADR 0005)"
```

## Project definition files (the architect writes these in *model*)

```yaml
architecture: .mismagent/architecture.md
code_rules: .mismagent/code-rules.md
```

## Sides

```yaml
sides:
  app:                          # single side, one Kotlin codebase (ADR 0001), fully local processing
    repo: .
    # dev-architecture (style memory) — NOT authored yet: a separate targeted style dispatch will
    # write .mismagent/architetture/dev-architecture-app.md (deliberated with the user) BEFORE the
    # first domain wave and switch this binding to that path.
    dev_architecture: none
    gate: "./gradlew check"     # compile (allWarningsAsErrors) + detekt + unit/contract/Konsist tests + Compose UI tests (:ui:renderCheck) + verificaDipendenzeModuli + verifySqlDelightMigration; headless, no model weights, no native ML libs
    toolchain: "Kotlin 2.x/JVM on JetBrains Runtime 21 (Gradle toolchain, foojay resolver); Gradle wrapper + Kotlin DSL + version catalog; Compose Multiplatform Desktop; SQLDelight; Konsist; detekt. Opt-in (outside the gate): ./gradlew modelliTest (real ML adapters, @Tag(\"modelli\"), downloads natives+models) and ./gradlew benchmarkElaborazione -Pcampione=<60-min sample> (NFR, ADR 0011)"
    ui_render_check: "./gradlew :ui:renderCheck"   # Compose Desktop headless (runComposeUiTest/ImageComposeScene): every screen S1–S4 + lettore-audio from fixture read-models at 1280x800 and 1024x640, all states (empty/loading/error/data); PNGs to ui/build/render-check/; semantic asserts on key nodes + no clipped text. Part of the gate.
    run: "./gradlew :avvio:run"                     # native window, no port. Smoke: ./gradlew :avvio:run --args="--smoke <fixture-progetto-dir>" (opens the fixture, captures one screenshot per screen to avvio/build/smoke/, exits 0)
    contract: none
```

## Domain bounded contexts (seed)
- `Progetto` — projects and the recordings they accumulate over time
- `Trascrizione` — speech-to-text of a recording, segmented by speaker (IT/EN)
- `Parlanti` — speaker identities of a project, voice-print matching, naming (recurring vs one-off)
- (v2, NOT modeled yet) `Sintesi` — chapters and summaries by a local open-source LLM on top of the transcript

## Constraints
- All models open-source and running locally (no cloud).
- Desktop app, cross-platform (user explicitly chose a desktop app over CLI/local-web, against the challenger's advice). User is on macOS; nothing Mac-only.
- Typical use: work meetings, 2–4 speakers, IT + EN mixed, recurring people + one-off guests.

## Boundaries & projection
Single side → every boundary is `in-process` (port + contract test). No OpenAPI.

## Boundary rules
- The boundary is the MODULE/package: never write outside your own block's package; the other context is touched only via the port.
- Never commit secrets / .env / audio samples / model weights / DB files. **Scoped exception (ADR 0006):** the SQLDelight schema snapshots `persistenza/src/main/sqldelight/databases/*.db` (schema only, never user data) ARE committed — `verifySqlDelightMigration` needs them.
