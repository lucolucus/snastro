VERDICT: PASS
SHA: 982c3132d39f6c0468eefa11baef6139b2f38878
SCORES: simple=4 naming=4 modularity=4 duplication=5 concision=4 errors=- tests=4

Slice review of 06-test-accettazione-in-tests-acceptance (refactor), diff 15b3682d..HEAD.

Evidence run:
- `mm gate`: test green (./gradlew check), lint green (./gradlew detekt), check green.
- `avvio/build/test-results/test/TEST-snastro.avvio.accettazione.EliminaProgettoDallaListaTest.xml`: tests=3 failures=0 errors=0, so the class compiles and runs from the new srcDir.
- `mm check --base 15b3682d`: 1 error. `.claude/skills/conventions/references/acceptance-tests.md` changed since base (it is the human's file). See N1 below.
- I also ran a scratch copy of HEAD with the skill file put back to its base text. `mm check` then fails with "cites avvio/src/test/.../EliminaProgettoDallaListaTest.kt, which does not exist". So the slice could not avoid this error: whichever way it goes, one mm check rule is red.

| dimension | score | evidence |
|---|---|---|
| simple | 4 | `avvio/build.gradle.kts:87-91`: a 3-line srcDir plus detekt hookup, with a comment that says why. Test: one test dispatcher for the presenter, and the real session keeps a real scope (`EliminaProgettoDallaListaTest.kt:51-53`). |
| naming | 4 | `scopePresenter` / `scopeSessione` / `testDiAccettazione` name what they hold. |
| modularity | 4 | The test stays a `:avvio` test, as the topic decides (no new module or edges). `ProgettiPresenter` is now built on the test dispatcher (`:122`). |
| duplication | 5 | Reuses `RegistroProgettiFinta`, `componentiDiProva` and `attendiFinche`. No copies. |
| concision | 4 | `.also { advanceUntilIdle() }` (`:123`) is tight. The KDoc lists the substituted boundaries without padding (`:37-44`). |
| errors | - | not touched |
| tests | 4 | EX-1..3 have the same given/when/then as at base (compared with `git show 15b3682d:avvio/src/test/.../EliminaProgettoDallaListaTest.kt`). The only change is the scheduling: `runTest(dispatcher)` + `advanceUntilIdle()` after each action, and both scopes are cancelled in `@AfterEach` (`:58-63`). No `Dispatchers.Unconfined` is left under tests/. `SessioneProgettoImpl.elimina` is synchronous (`SessioneProgettoImpl.kt:197`), called through the presenter's `withContext(io)` with io = test dispatcher. So `advanceUntilIdle` covers the deletion, with no race against the Default-dispatched session scope. |

Examples: EX-1, EX-2, EX-3 are proven by the moved tests, nothing weakened, all pass. The EX markers are kept in the test names.

Conventions: the diff follows topic acceptance-tests (location, srcDir, KDoc boundaries, runTest/StandardTestDispatcher, teardown cancel). It does two new things: `detekt { source.from(...) }`, and the presenter-only test dispatcher with the session on a real scope. Both are proposed in `.mismagent/conventions-proposals.md`, and the proposal matches the diff.

## Findings

No blocking findings.

N1 (method conflict, for the conductor, not blocking the code): `mm check --base` is red because the slice changed the human-owned skill file (`acceptance-tests.md:18-19`, only the "Shown by" path, from "today still in the old place, to be moved by the refactor slice 06" to the new path). That exact change was announced in the skill text at base. Reverting it makes the plain `mm check` / gate red (the skill would cite a missing file). The edit is logged in conventions-proposals.md. The conductor should ratify it through the conventions flow (or accept it as is). The code needs no rework.

Advice (taste):
- `EliminaProgettoDallaListaTest.kt:53`: `CoroutineScope(SupervisorJob())` runs the session on Dispatchers.Default implicitly. Naming the dispatcher (`+ Dispatchers.IO` or a comment) would make the "real scope" choice visible at the declaration and not only in the KDoc.
