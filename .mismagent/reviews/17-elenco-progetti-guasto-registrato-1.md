VERDICT: REWORK
SHA: c06486d3b0a2c358d6a6f3039ee800618a838ff5
SCORES: simple=5 naming=4 modularity=- duplication=5 concision=5 errors=5 tests=4

# Review: 17-elenco-progetti-guasto-registrato (refactor), base 621d9586

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ui/src/main/kotlin/snastro/ui/progetti/ProgettiPresenter.kt:45-52`: one call to the helper, then one state assignment. The 16-line try/catch is gone. |
| Naming | 4 | `"carica l'elenco dei progetti"` names the operation in the log line. The KDoc at `:43-44` keeps the tracking ids (`fix-batch-12 #5, L530d`). That is fine but not needed. |
| Modularity | - | not touched |
| Duplication | 5 | the hand-written catch is replaced by the shared helper `GuastoNonFatale.kt:15-18`, so nothing is duplicated |
| Concision | 5 | the `CancellationException` import and the `@Suppress` are removed. The CR-19b entry is removed (`RegoleArchitetturaliTest.kt:721`, before the change) |
| Error handling | 5 | CR-7: the cause is now logged at WARNING. ADR 0041 §3 explicitly names "S1's list load" for `nonFataleRegistrato`, and this follows it. The visible state is unchanged. |
| Tests | 4 | `ProgettiPresenterTest.kt:428-447` throws through the registry seam. It asserts the WARNING carries that cause, then `erroreElenco`, then Riprova. The builder reports it was red before the fix. Forced re-run: 20/20 green, and the new testcase is in the XML. It checks two things (the log, and Riprova still reachable), but the slice asks for exactly that. |

Checks run:
- `MM gate`: green. This ran `./gradlew check` and detekt.
- `./gradlew :ui:test --rerun --tests 'snastro.ui.progetti.*'`: green.
- `:architettura-test:test --rerun`: green.
- `MM check --base 621d9586`: **exit 1** (details below).

Examples: none. This is a refactor and no other example's test was edited.

## Blocking

1. **Rule: the conventions skill is the human's file (`MM check --base` is red).**
   - `.claude/skills/conventions/references/non-fatal-faults.md:22-23` was edited in 382082f5. It adds `ProgettiPresenter.kt (caricaElenco)` to "Shown by".
   - `mm check` reports: `error: ... changed since 621d9586 (the human's file: specify or conventions changes it)`.
   - **Fix:**
     - Restore the file: `git checkout 621d9586 -- .claude/skills/conventions/references/non-fatal-faults.md`, then commit.
     - If the citation is wanted, write it as one entry in `.mismagent/conventions-proposals.md`, which is empty today. The convention itself needs no change, because ADR 0041 §3 already covers S1's list load.
   - The code change itself is clean.

## Advice

- Behaviour widens a little. The old code caught `Exception`. `nonFataleRegistrato` catches every `Throwable` except cancellation and `VirtualMachineError`, so an `AssertionError` from the read now becomes the banner. ADR 0041 §3 accepts this for reads (no commit), so this does not block.
