VERDICT: PASS
SHA: 8cd05227
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=5 tests=5

# Review 19-s2-bordi-registrati (slice, refactor), round 1

Base `7e2cd606`..HEAD `8cd05227`.

## Evidence run
- `mm gate`: exit 0 (`check: green`, `gate: green`; the only output is the old warnings about ADRs with no Evidence section).
- `mm check --base 7e2cd606`: `check: ok`, exit 0.
- I forced a fresh run with `./gradlew :ui:test --rerun :ui:detekt --rerun` and `:architettura-test:test --rerun`: BUILD SUCCESSFUL. From the XML: ui 691 tests, 0 failures, 0 errors, 0 skipped. `RegistrazioniConsegnaDopoCommitTest` 4/4 and `RegistrazioniPresenterAggiornamentoTest` 4/4 ran at 17:46-17:47Z today. The architecture tests (CR-19b frozen counts) are green.
- **Mutation check.** I extracted HEAD with `git archive` into the scratchpad (no worktree, nothing under the project root). There I put back the base `RegistrazioniPresenter.kt` and kept the HEAD tests. Both new tests then FAIL: `L269 un Error dopo il commit di Trascrivi N parti…` and `CR-7 un refresh fallito registra un WARNING…` (8 run, 2 failed). So both tests prove the change and would catch it being undone.

## Examples
The slice has no examples (refactor). No test of an existing example was edited. The diff to the tests only adds two tests.

## The table (on the diff)
| dimension | score | evidence |
|---|---|---|
| simple | 4 | `carica` (RegistrazioniPresenter.kt:155-165) is now one `when` with the failure branch and the L485b generation branch. `avviaElaborazioniIncontro` (:624-639) is try/finally around one command. |
| naming | 4 | Operation labels are in domain Italian (`"carica le registrazioni"`, `"leggi gli incontri"`, `"trascrivi $id"`). The KDoc cites ADR 0041 §3, D-0065 and L269. |
| modularity | 4 | It uses only the `:ui` helpers from `GuastoNonFatale.kt`. The `snastro.supporto.catturaNonFatale` import is gone from the presenter. No new dependency. |
| duplication | 4 | Five hand-written try/catch blocks became one helper call each. The only small repeat: the operation name is passed to both `eccezioneRegistrata` and `comandoConfermato` (`"trascrivi $id"` :626-627, `"import"` FlussoImporta.kt:132-133). The builder already noted this in progress as a design note, and `StatoS2.comandoDiRiga` has the same shape. |
| concision | 4 | -66/+95 lines in total. In the code alone, roughly 60 lines of catch scaffolding and 5 `@Suppress` are removed. No new suppression: no added line contains `@Suppress`. |
| errors | 5 | Each of the ADR 0041 §3 choices is applied: committing commands (`avviaElaborazioniIncontro` :626, `FlussoImporta.invia` FlussoImporta.kt:132) use `eccezioneRegistrata`; reads and the player (`carica` :157, `gruppi` :273, `precompilato` :330, `conteggiIdentificazione` :399, `riproduci` :679, `pausa` :690) use `nonFataleRegistrato`. Every failure is logged with its cause. `operazioneInCorso` is reset in `finally` (:637). The callers of `invia` already had `finally` blocks (FlussoImporta.kt:55, :88, :114). `grep` finds no remaining catch-all or `catturaNonFatale` in `ui/.../registrazioni/`. The only catches left are typed ones: `PercorsoTrascinato.kt:87` `CharacterCodingException` and `SchermataRegistrazioni.kt:1216` `DateTimeParseException`. |
| tests | 5 | Each new test checks one behaviour at the presenter seam and is named for it. The L269 test asserts that exactly one `AssertionError` reached the scope's handler, so the command really was sent, and that the Incontro is released. The CR-7 test asserts WARNING + `causatoDa(guasto)` and that the error state is shown. Both were red before the change (see the mutation check). |

Checked against `ARCHITECTURE.md` (lines 26-30, the ADR 0041 summary) and `conventions/references/non-fatal-faults.md`: everything matches, and nothing is done a second way. The first-of-its-kind citations are proposed in `.mismagent/conventions-proposals.md`, and the proposal matches the diff: the same functions, files and tests.

CR-19b: the `RegistrazioniPresenter.kt` (4) and `FlussoImporta.kt` (1) entries are removed (RegoleArchitetturaliTest.kt:725-726). No count was moved to another file.

## MED residuo at pre-release.md:291: the closure holds
The residuo asked for three things: rethrow `Error`, reset in `finally`, test with an `AssertionError`.
- `eccezioneRegistrata` sits on `catturaEccezione`, which catches `Exception` only, so an `Error` propagates (GuastoNonFatale.kt:25-26). The reset is in `finally` (RegistrazioniPresenter.kt:636-638). The test is `RegistrazioniConsegnaDopoCommitTest` L269 'Trascrivi N parti'; it fails on the base code and passes at HEAD.
- The Error case no longer shows `MESSAGGIO_ERRORE_GENERICO`: `errore` stays at the `null` set when the command starts.
- An after-commit `ConsegnaDopoCommitFallita` still gives `Ok` through `comandoConfermato` (existing L270 test, green).
- The line's location `:814-823` is out of date (the code is now at :624-639), but it points at the right thing and the closing note names the slice and the test.

## Advice (non-blocking)
- RegistrazioniPresenter.kt:679-680 and :690-691 use `?:` on a `Unit?` to trigger the fallback on failure. It is correct because `LettoreAudio.riproduciDa` and `pausa` return `Unit` (LettoreAudio.kt:19, :25). But if either ever returns a nullable value, the fallback would fire on success. An explicit `if (… == null)` would make this robust.
- After an `Error` following the commit, 'Trascrivi N parti' does not reload the list (`carica()` is skipped). This is the same shape as the open MED at pre-release.md:290 (import) and is consistent with D-0065, which treats the Error as fatal. It is outside this residuo.
- Behaviour change, as ADR 0041 §3 intends: reads and the player now also catch non-fatal `Error`s such as `AssertionError`. Before, they caught only `Exception`. The builder recorded this in progress.md.
