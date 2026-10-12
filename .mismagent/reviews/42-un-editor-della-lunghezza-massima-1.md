VERDICT: PASS
SHA: cae2d3d42f019e142463fb6db61331941343d529
SCORES: simple=5 naming=5 modularity=4 duplication=4 concision=5 errors=- tests=4

Slice 42 (refactor, narrowed by its Decision of 2026-10-12 to the shared trim/parse/range rule and its tests).
Diff reviewed: `git diff 95b3f17b..HEAD`. `mm gate`: green. `mm check --base 95b3f17b`: green (only pre-existing ADR
"no Evidence" warnings).

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ui/src/main/kotlin/snastro/ui/impostazioni/LunghezzaMassima.kt:8-9`: one expression; `RiassuntoPresenter.kt:377-378` drops the hand-written range check in favour of it |
| Naming | 5 | `lunghezzaMassimaDa(testo, minimo, massimo)` uses the glossary words; KDoc states the rule and AC-S138 |
| Modularity | 4 | `internal` in `snastro.ui.impostazioni` (the setting's owner, as the slice says); `RiassuntoPresenter.kt:28` cross-screen import inside `:ui` is allowed by `code-rules.md:29-30` and already done (`snastro.ui.coda`, `snastro.ui.modelli`) |
| Duplication | 4 | the two parse rules (`LunghezzaRiassuntoPresenter.kt:58`, `RiassuntoPresenter.kt:377`) now call one function; the save step is still written twice, deferred by the Decision and recorded in `design-notes.md` (last line) |
| Concision | 5 | nothing unused; no new layer |
| Error handling | - | not touched (refusal path and message unchanged, `RiassuntoPresenter.kt:378-381`) |
| Tests | 4 | `RiassuntoPresenterTest.kt:634-643` (" 500 " with bounds 300..2500, fixture per `:602`, sends `[500]`; red before since `" 500 ".toIntOrNull()` is null), `LunghezzaRiassuntoPresenterTest.kt:75-86` (same case, pins Impostazioni); one behaviour each, at the presenter seam; no existing test edited (diff is additions only) |

## Blocking
None.

## Checks against the slice and Decision
- Decision: " 500 " saves 500 in both screens — proven by the two new tests; out-of-range stays refused (existing
  tests unchanged and green).
- `RiassuntoPresenter` constructor untouched; `:avvio` untouched; views untouched.
- No new suppression. Nothing first-of-its-kind (an `internal` top-level function in a `:ui` screen package has
  precedent), so no conventions proposal is owed.
- Deferral of the holder extraction and the shared save step is recorded in `.mismagent/design-notes.md`.

## Advice (not blocking)
- `RiassuntoPresenterTest.kt:636` is named `AC-S138 ...`, but AC-S138 says nothing on spaces (the slice says so);
  the comment at `:634` explains it, a name without the AC prefix would be more honest.
- The slice asked to report the `TooManyFunctions` count of `RiassuntoPresenter` without the suppression;
  `progress.md` does not give it (narrowed scope makes it moot for now).
