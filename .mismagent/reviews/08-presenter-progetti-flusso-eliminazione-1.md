VERDICT: PASS
SHA: 8534df6a8d085c88c2365f8bc21806ebca3c0e43
SCORES: simple=4 naming=4 modularity=5 duplication=5 concision=4 errors=4 tests=4

# Review: 08-presenter-progetti-flusso-eliminazione (slice, refactor), round 1

Base `a0c6dc30`. What I ran myself at HEAD: `./gradlew check` gave exit 0. `mm gate` was green and `mm check --base a0c6dc30` was ok. The only warnings were the old "no Evidence:" lines on decisions, which are not part of this diff.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `ProgettiPresenter.kt` now has 6 functions and is back under the TooManyFunctions threshold of 11. The `chiudiAvvisoEliminazione` lambda written only to stay under the limit is gone: it is a real function again at `FlussoEliminazione.kt:53`. `azioni` wires the holder's functions directly (`ProgettiPresenter.kt:98-101`), so there are no forwarding members. |
| Naming | 4 | `CorsaSingola` and `FlussoEliminazione` are domain words, and `chiedi`/`annulla`/`conferma`/`chiudiAvviso` read naturally inside the holder. |
| Modularity | 5 | Each holder has one reason to change. `CorsaSingola.kt:18` owns M3, M1(b) and the `inCorso` reset. `FlussoEliminazione.kt:14` owns the confirmation, the `elenco` reload and `cartellaUtente`. Both are `internal`, and the public API (constructor, `stato`, `azioni`) is the same as before. |
| Duplication | 5 | There is one runner for both holders. The raw catch with `@Suppress` that `avvia` used to have is gone, and `ProgettiPresenter`'s frozen CR-19b count goes from 2 to 1 (`RegoleArchitetturaliTest.kt:722`). |
| Concision | 4 | Nothing is left unused. `cartellaUtente` is now a plain constructor parameter, no longer a property (`ProgettiPresenter.kt:31`). `CorsaSingola.dati` (`:24`) has one user (`FlussoEliminazione.kt:35`) and is fine as it is. |
| Error handling | 4 | `CorsaSingola.kt:49` uses `catturaNonFatale`, which is the CR-19b way for new code. Cancellation and `VirtualMachineError` are still rethrown, and the `finally` reset stays at `:54-60`. |
| Tests | 4 | The tests changed only at call sites: `presenter.x` became `presenter.azioni.x` in `ProgettiPresenterTest.kt:221-222,238-239` and in the three acceptance files. Every given, when and then is unchanged, and the EX names are kept. Going through `azioni` also tests the wiring the views use. |

## Blocking findings

None.

## Examples

The slice has no examples of its own (`Examples:` is empty, refactor). The tests for EX-1..3, EX-6 and EX-9..11 changed only at call sites (diff of `EliminaProgettoDallaListaTest.kt`, `EliminaSoloContenutiSnastroTest.kt`, `EliminaConLockECodaTest.kt`), and they are green.

## Conventions

The first split of a presenter is proposed in `.mismagent/conventions-proposals.md` (`create presenter-split`). The proposal matches the diff: internal holders over the one `MutableStateFlow`, one shared single-flight runner, and `azioni` wiring the holders with no forwarding members.

## Advice (not blocking)

- **Behaviour change (reported by the builder, confirmed):** a non-VM `Error` thrown by `crea`/`apri`/`elimina` (for example `AssertionError` or `NoClassDefFoundError`) now becomes `MESSAGGIO_ERRORE_GENERICO`. Before, it escaped `scope.launch`. This is the sanctioned policy (CR-7 / ADR 0028 §2, `CatturaNonFatale.kt`) and the same as the other `catturaNonFatale` users in `ui`. No test pins it. If it matters, a test for "an Error from elimina shows the generic message" would make the change explicit.
- The throwable is dropped and not logged (`.getOrNull()` at `CorsaSingola.kt:49`). This was already true before (the old code had `SwallowedException` suppressed) and is the same as `RegistrazionePresenter.kt:215`. It is out of scope here, but a fault that is only ever shown as the generic message is hard to diagnose.
- Process: intermediate commit `561e95ec` was red on CR-19b. It was fixed in `44b2b679`, and HEAD is green.
- Outside the diff: `.claude/skills/conventions/references/confirm-by-typing.md:14` still cites `ProgettiPresenter.kt (confermaElimina)`. It is logged as one line in `design-notes.md`.
