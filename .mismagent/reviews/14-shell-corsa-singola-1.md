VERDICT: PASS
SHA: f8d4920b4c330c8e9424ab6989103517665e888d
SCORES: simple=5 naming=4 modularity=4 duplication=5 concision=5 errors=5 tests=4

# Review 14-shell-corsa-singola (refactor), base da5e014e

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ShellPresenter.kt:71-99`: apri/crea/chiudi are one-liners over `corri`; the runner does one thing (guard, Caricamento, run, apply-or-generic, reset) |
| Naming | 4 | `corri(cosa, operazione, applica)` (`:88`) in the codebase's Italian; `cosa` doubles as the log label, matching `CorsaSingola.avvia(cosa, …)` |
| Modularity | 4 | stays in `:ui`, uses the `:ui` helper `nonFataleRegistrato` (`GuastoNonFatale.kt`); `EliminazioneProgettoAperto` untouched, as presenter-split "The shell" requires |
| Duplication | 5 | the two hand-written single-flight copies (old `avvia` and `chiudi`) are gone; one runner, as presenter-split "Shell operations" decides |
| Concision | 5 | net -17 lines in `ShellPresenter.kt`; `CancellationException` import and the two `@Suppress` removed; nothing unused |
| Error handling | 5 | `:92` logs the cause at WARNING under the operation name via the non-fatal-faults helper; cancellation propagates (`catturaNonFatale` rethrows it), `finally` keeps L457a (`:94-97`); stale "no logging sink" comment removed; CR-19b frozen entry for `ShellPresenter.kt` removed (`RegoleArchitetturaliTest.kt:716`) and the rule passes |
| Test quality | 4 | `ShellPresenterTest.kt:363,374`: one behaviour each (apri / chiudi fault logged with its cause), through the public presenter with the `registriLogUi` spy |

## Evidence run
- Forced reruns (`--rerun`): `ShellPresenterTest` 20/20, `ShellSelettoreProgettoTest` 4/4, `EliminazioneProgettoApertoTest` 10/10 (`:ui`), acceptance `EliminaProgettoApertoTest` 2/2 (`:avvio`), `:architettura-test` all green (RegoleArchitetturaliTest 35/35).
- `./gradlew check`: exit 0. `mm gate`: green.
- `mm check --base da5e014e`: 1 error, `.claude/skills/conventions/references/non-fatal-faults.md` changed (the human's file). See A1.
- Examples: none in scope (refactor). No pre-existing test edited; only two tests added. No new suppression (two removed).

## Blocking
None.

## Advice
- **A1 (process, not a code defect).** `mm check` flags the conventions file. The change only completes the "Shown by" line the topic itself anticipated ("Still the defect, slice 14: ShellPresenter.kt"), and it matches the diff (`corri` is a user of the helper). Same situation as slice 12's review A1; `mm land` may need the human/conventions step to accept it.
- **A2 (taste).** Behaviour widened slightly: the old catch was `Exception`; `catturaNonFatale` catches any `Throwable` except `VirtualMachineError`/cancellation (e.g. an `AssertionError` from `sessione.apri` now shows the generic message instead of crashing the scope). This is the convention's decided policy, consistent with `CorsaSingola`, so not a block.
- **A3 (taste).** No test covers `crea` logging specifically; it shares the same `corri` path as `apri`, so coverage is adequate.
