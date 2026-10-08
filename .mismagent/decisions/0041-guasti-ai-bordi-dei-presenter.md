---
scope: global
status: accepted
supersedes: null   # partial, amended in place with a dated pointer: ADR 0028 §2 (`:supporto` gains `catturaEccezione`, CR-18c list)
closes_spike: null
decided: 2026-10-08 · architect (design pass R0, release review R0-2; the design note on S2's raw catches asked for an architect's decision)
from_note: design-notes.md (ui S2 raw `catch (e: Exception)`), release review R0-2 (errors=3, duplication=3)
enforced_by: []   # CR-18c (Konsist, the pinned `:supporto` list) and CR-19b (frozen catch counts) already gate the shape; the L269 tests gate Error propagation
---
# 0041 — Faults at a presenter's edge: a committing command catches `Exception` only, everything else `catturaNonFatale`; both log

## Context
- CR-7 and the conventions topic non-fatal-faults forbid dropping a cause unlogged. `nonFataleRegistrato`
  (`ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt:15-18`) logs it, but it sits on `catturaNonFatale`, which catches
  every `Throwable` except `CancellationException` and `VirtualMachineError`
  (`supporto/src/main/kotlin/snastro/supporto/CatturaNonFatale.kt:11-19`), so it also catches `AssertionError`.
- D-0065 (`features/incontro/decisions.md:857`): every `Error` after a commit is fatal and must propagate. The L269 tests
  check this for S2 and S4 (`ui/src/test/kotlin/snastro/ui/registrazioni/RegistrazioniConsegnaDopoCommitTest.kt:149`,
  `ui/src/test/kotlin/snastro/ui/parlanti/ParlantiPresenterTest.kt:165`). They pass today because S2 catches
  `Exception`, not `Throwable` (release review R0-2 ran them green at f62d7614).
- So S2 keeps eight raw `catch (e: Exception)` blocks that drop the cause (release review R0-2, duplication and errors).
  Three of them are the same per-row runner written by hand (`RegistrazioniPresenter.kt:541-571`, `:637-667`,
  `FlussoEliminaRiga.kt:59-88`).
- `avviaElaborazioniIncontro` (`RegistrazioniPresenter.kt:607`) commits through `catturaNonFatale`. An `Error` after its
  commit is swallowed, against D-0065. This is the open MED residuo at `features/incontro/pre-release.md:291`. The
  review's suggested fix (`nonFataleRegistrato`) would keep that defect, so it is not taken.
- CR-19b (`code-rules.md:159`): catch-alls in `src/main` live in `:supporto`, plus the frozen per-file counts
  (`architettura-test/.../RegoleArchitetturaliTest.kt:698-732`). A count may only go down.

## Decision
1. **`:supporto` gains `catturaEccezione(blocco): Result<T>`.** It catches `Exception` only and rethrows
   `CancellationException`. An `Error` is never caught, so it propagates. It is the twin of `catturaNonFatale`. ADR 0028
   §2's pinned list and CR-18c's list both gain it (dated amendment in ADR 0028).
2. **`:ui` gains `eccezioneRegistrata(operazione) { … }: T?`** next to `nonFataleRegistrato`, in `GuastoNonFatale.kt`.
   It is `catturaEccezione` plus the same WARNING log line, through one private log function the two helpers share.
3. **Which one to use:**
   - A block that runs a **committing command** (`comandoConfermato`, an import) uses `eccezioneRegistrata` (D-0065).
   - **Everything else** at a presenter's edge uses `nonFataleRegistrato`: reads, refreshes, the player, S1's list load.
   - Cleanup stays in `finally`. Refusals stay `Esito`.
4. **S2's per-row commands share one runner on `StatoS2`.** It owns the row guard, the in-flight flag,
   `azzeraAvviso`, the launch, the fault mapped to the generic `erroreRiga` through `eccezioneRegistrata`, and the reset
   in `finally`. Callers pass only what happens on `Ok` and on a refusal (presenter-split, S2).
5. **CR-19b counts.** Every S2 catch moves onto a helper and its frozen count drops to 0. No count is transferred to
   another file.

## Considered
- **Log inside each raw catch:** no new API, but the eight copies stay, along with the duplication the review scored 3.
- **Make `nonFataleRegistrato` catch `Exception` only:** one helper. But it would change S1's and the shell's contract
  (presenter-split: `CorsaSingola` maps every non-fatal fault), with no test or decision asking for that.
- **Put the `Exception` catch in `:ui` (`GuastoNonFatale.kt`):** this would add a new frozen CR-19b entry. CR-19b
  places catch-alls in `:supporto`, and slice 16's redistribution was explicitly not a general permission
  (presenter-split, S2).

## Consequences
- There are two catch helpers and the rule picks one by whether the block commits.
  - Judgment: this distinction is already in the code (S2 and S4 catch `Exception`, S1 and the shell catch through
    `catturaNonFatale`).
  - Open question: S1's `crea` also commits through `catturaNonFatale`, so under D-0065 it would swallow an `Error`.
    This is left as a design note. A change there is the human's call on S1's contract.
- S4 (`ParlantiPresenter.suRiga`, the other L269 site) moves onto `eccezioneRegistrata` with the next change there.

## Evidence
- `supporto/src/main/kotlin/snastro/supporto/CatturaNonFatale.kt:11-19`: what `catturaNonFatale` catches.
- `ui/src/main/kotlin/snastro/ui/GuastoNonFatale.kt:15-18`: the current helper.
- `ui/src/main/kotlin/snastro/ui/registrazioni/RegistrazioniPresenter.kt:541-571,596-620,637-667`,
  `FlussoEliminaRiga.kt:59-88`, `FlussoImporta.kt:123-136`: the S2 catches, read at f62d7614.
- `RegistrazioniConsegnaDopoCommitTest.kt:149-175`: an `AssertionError` from a committing command must reach the
  scope's handler 4 times.
- `features/incontro/decisions.md:857-868` (D-0065). `features/incontro/pre-release.md:291` (the MED residuo).
- `code-rules.md:148` (CR-18c), `:159-162` (CR-19b).
- `RegoleArchitetturaliTest.kt:560-569` (the pinned API set) and `:698-732` (the frozen counts).
- `.mismagent/reviews/R0-2.md`: the release review.
