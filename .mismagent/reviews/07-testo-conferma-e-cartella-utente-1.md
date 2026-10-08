VERDICT: PASS
SHA: ac25d9bafa4471889c66a789d1186ddfa9d684c5
SCORES: simple=5 naming=5 modularity=5 duplication=4 concision=5 errors=- tests=4

# Review — 07-testo-conferma-e-cartella-utente (slice, refactor), round 1

Base `b6d474cf`. Diff: `ProgettiPresenter.kt`, `TestiProgetti.kt`, `TestiProgettiTest.kt`, `EliminaProgettoDallaListaTest.kt`.

Evidence I produced:
- `mm gate`: green (exit 0; `./gradlew check` passed in full, including `ControlliAdrTest`; no adr-0034 timeout on this run).
- `mm check --base b6d474cf…`: ok (only the old "no Evidence: section" warnings on decisions, not from this diff).

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ui/.../progetti/ProgettiPresenter.kt:36`: the default is gone, nothing added |
| Naming | 5 | `ui/.../testi/TestiProgetti.kt:36-39`: KDoc names EX-9 and what the text promises |
| Modularity | 5 | `:ui` still never reads `user.home`; `:avvio` passes `cartellaUtenteReale()` (`avvio/.../Presenter.kt:39`), as display-paths requires |
| Duplication | 4 | `TestiProgettiTest.kt:31-35` repeats the literal on purpose, to pin the wording. That is right for a copy test |
| Concision | 5 | diff is +20/−5 and has nothing unused |
| Error handling | - | not touched |
| Test quality | 4 | `TestiProgettiTest.kt:30`: one behaviour, named after it, tagged EX-9; `EliminaProgettoDallaListaTest.kt:122`: passes `cartellaUtente = ""` explicitly, as the convention asks |

## Examples
- EX-1: `EliminaProgettoDallaListaTest.kt:70`. The given, when and then are the same as before. The only change is the explicit constructor argument at line 122. Green.
- EX-9: the acceptance test `EliminaSoloContenutiSnastroTest.kt:79` was not edited and is green. The new confirmation text is pinned to the exact sentence the slice asks for, at `TestiProgettiTest.kt:30-36`.
- No other example's test was edited.

## Conventions
- display-paths (`.claude/skills/conventions/references/display-paths.md`) says "no constructor default; `""` passed explicitly means unknown". The diff does this. Every call site of the constructor passes the argument: `Presenter.kt:39`, `EliminaSoloContenutiSnastroTest.kt:140`, `EliminaProgettoDallaListaTest.kt:122`, `ProgettiPresenterTest.kt:115-121`.
- The diff does nothing for the first time, so it needs no proposal.

## Findings
No blocking findings.

Advice (not blocking):
1. `ProgettiPresenterTest.kt:112`: the private builder `presentatore(…)` still has `cartellaUtente: String = ""` as a fixture default. It passes the value explicitly to the constructor, so it keeps to the letter of display-paths. Still, a test that forgets the home gets "unknown" silently. This is outside the diff and was already noted in progress.md.
2. `.mismagent/design-notes.md:7` (the confirmation wording "is no longer exact since slice 02") is resolved by this slice. It can be struck when the slice lands.
