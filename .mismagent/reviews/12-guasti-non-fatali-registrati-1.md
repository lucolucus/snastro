VERDICT: PASS
SHA: d62d80bef25cca80cb5534783c4a487504367b5e
SCORES: simple=5 naming=4 modularity=4 duplication=5 concision=4 errors=5 tests=4

# Review — 12-guasti-non-fatali-registrati (refactor), base 60c5531f

## Evidence run
- `mm gate` (`./gradlew check`, Konsist CR-19 included): **green**.
- Touched tests forced: `./gradlew :ui:test --rerun --tests snastro.ui.EliminazioneProgettoApertoTest --tests snastro.ui.progetti.ProgettiPresenterTest`
  → 9/9 and 19/19 pass, both new `CR-7 …` cases among them (results XML read at 2026-10-08T16:06).
- `mm check --base 60c5531f`: 1 error — `.claude/skills/conventions/references/non-fatal-faults.md` changed (human's file). See finding A1.
- `tests/acceptance/`: no change in the diff. Existing tests in `ProgettiPresenterTest` / `EliminazioneProgettoApertoTest`: only additions.

## Scores

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt:15-18` one three-line helper; call sites shrink: `CorsaSingola.kt:53`, `EliminazioneProgettoAperto.kt:53` |
| Naming | 4 | `nonFataleRegistrato`, `logGuastiNonFatali` in domain Italian; `cosa` (`CorsaSingola.kt:40`) mirrors `comandoConfermato(cosa, …)` (`ComandoConfermato.kt:20`); operation names read as actions (`ProgettiPresenter.kt:74,82`, `FlussoEliminazione.kt:38`, `FlussoTogliDallaLista.kt:20`) |
| Modularity | 4 | helper is `internal` in `:ui`, logger `snastro.ui.GuastoNonFatale` (`GuastoNonFatale.kt:8`) is a child of `snastro`, same scheme as `ComandoConfermato.kt:9`; no new dependency |
| Duplication | 5 | the catch+log+null is in one place; both runners use it; the test spy is shared (`ui/src/test/kotlin/snastro/ui/LogUi.kt`) |
| Concision | 4 | nothing unused; `loggerUi` strong ref (`LogUi.kt:8`) is justified in its KDoc |
| Error handling | 5 | CR-7 satisfied: `.onFailure { log(WARNING, "$operazione: …", it) }` (`GuastoNonFatale.kt:17`); cancellation still propagates via `catturaNonFatale`; user-visible M1(b) unchanged (`CorsaSingola.kt:56`); no new raw catch (CR-19b gate green) |
| Test quality | 4 | one test per runner at the presenter seam (`ProgettiPresenterTest.kt:242`, `EliminazioneProgettoApertoTest.kt:151`), asserting generic message + WARNING + cause + operation; `causatoDa` (`LogUi.kt:34-35`) handles coroutine stack-trace recovery copies, documented |

## Findings

No blocking findings.

### Advice / non-blocking
- **A1 (process).** `mm check` flags the conventions reference `non-fatal-faults.md` as a human's file changed in the
  slice. The topic itself asked for it ("cite it here once it exists", base version), the dispatcher confirmed it,
  and the new text matches the diff (helper, its two users, the test spy, ShellPresenter left for slice 14). Not a
  code defect; `mm land` may need the human/conventions step to accept it.
- **A2 (tests).** The `ProgettiPresenterTest` case drives only `elimina` through `CorsaSingola`; `crea`/`apri`/`togli`
  pass their `cosa` untested. Acceptable (the runner is one; the slice asked one test per runner).
- **A3 (taste).** The assertion `"elimina" in registrato.message` would also pass for the other runner's label;
  asserting the exact operation string would pin it. Taste.
- Out of the diff: the remaining unlogged `catturaNonFatale { … }.getOrNull()` reads are already in
  `.mismagent/design-notes.md:10`; nothing new added there.
