VERDICT: PASS
SHA: f425fe944436d0582952eb1c675e262b593517e3
SCORES: simple=4 naming=4 modularity=- duplication=4 concision=5 errors=5 tests=4

# Review — 26-riassunto-guasti-registrati (slice, Base 70a8e62c)

Evidence run: `mm gate` exit 0 ("gate: green"; runs `./gradlew check` + detekt), `mm check --base 70a8e62c…` exit 0
("check: ok"). Only warnings: ADRs without an `Evidence:` section, none touched by this diff.

Examples: the slice is a refactor with no `Examples:`; no EX-tagged test was edited (diff touches only
`RiassuntoPresenterTest.kt`, `AmbienteRiassunto.kt`, `RiassuntoPresenterGuastiTest.kt` in tests).

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `RiassuntoPresenter.kt:274-290`: the `try`/`when` now yields `erroreDaMostrare` as an expression (Ok → null, Errore → message, null → generic); `:309-317` and `:399-404` each one call through one helper. |
| Naming | 4 | operation names in domain words (`"riassumi <id>"`, `"scarica il modello linguistico"`, `"riproduci la fonte di … da … ms"`); `avviato` (`:311`) reads well. |
| Modularity | - | no module edge touched. |
| Duplication | 4 | the three edges reuse `GuastoNonFatale.kt`'s two helpers; the fixture is shared once in `AmbienteRiassunto.kt` instead of copied into the new class. |
| Concision | 5 | two `@Suppress("TooGenericExceptionCaught", "SwallowedException")`, the `CancellationException` import/rethrows and the `catturaNonFatale` import removed; nothing new unused. |
| Error handling | 5 | ADR 0041 §3 / architecture amendment 2026-10-08 applied as written: committing `riassumiCmd` on `eccezioneRegistrata` (`:276`, `Error` propagates, reset in `finally` `:288`), non-committing download start and playback on `nonFataleRegistrato` (`:311`, `:401`); CR-19b count for the file 2 → 0 and entry removed (`RegoleArchitetturaliTest.kt:724`). Each path has a test. |
| Test quality | 4 | `RiassuntoPresenterGuastiTest.kt`: three tests, one behaviour each, through `azioni` + `registriLogUi`/`causatoDa`, assert WARNING, the operation and the identical cause; the riassumi test also asserts `MESSAGGIO_ERRORE_GENERICO`, the download test the re-read. Red-before plausible by construction (no log existed before). Pulled down by the comment finding below. |

Conventions: `.mismagent/conventions-proposals.md:2` proposes the "Shown by" citation for non-fatal-faults, as the
slice asks, and matches the diff (three edges, file and test paths correct). The skill was not edited.

## Blocking findings
None.

## Advice (fix in the next slice touching these tests — 22–25 will)
1. **Misplaced comment (tests/naming).** `RiassuntoPresenterGuastiTest.kt:36-40` carries the "Pre-release finding
   #151 … single-flight … at most ONE reload ever reaches its last read" comment. It explained
   `AC-S151 rework due trigger ravvicinati…` (base `RiassuntoPresenterTest.kt:310-314`) and was cut from there
   during the fixture move; it now sits above the download-fault test, which it does not describe, and AC-S151
   (`RiassuntoPresenterTest.kt:170`) lost its rationale. Fix: move the five lines back above
   `RiassuntoPresenterTest.kt:169`. (This is also the one edit to `RiassuntoPresenterTest` that the progress note's
   "moved verbatim … unchanged" does not cover.)
2. **Class KDoc inaccurate.** `RiassuntoPresenterGuastiTest.kt:15-16` says "what the user then sees is in
   [RiassuntoPresenterTest]", but the first test asserts the generic message (`:32`) and the second the re-read
   (`:54`). Drop the clause or say it.
3. **Fixture not quite verbatim.** `AmbienteRiassunto.kt:110,112,83-88` adds `guastoRiassumiCmd`,
   `guastoRiproduciDa` and `ServizioModelliSpia.guasto`; `error(...)` became `throw guasto…` (same type and message,
   so existing tests are unaffected). Fine, but the progress line "moved verbatim" overstates it.
4. Per the slice, a download-start fault and a playback fault show no generic message (re-read / Parte switch
   instead), which is narrower than the amendment's "shows M1(b)'s generic message". It is what the slice specified
   ("everything else the user sees stays the same"); worth an explicit line in the proposal or ADR 0041 if slices
   27–30 copy the pattern.
