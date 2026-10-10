VERDICT: PASS
SHA: cd66f0e1a2000b9d8d6e91e6d9c30e974860345b
SCORES: simple=5 naming=4 modularity=4 duplication=5 concision=4 errors=5 tests=4

Slice 31 (refactor, R1, no examples). Diff `0a605fbf..cd66f0e1`. `mm gate`: test green (`./gradlew check`), lint green
(`./gradlew detekt`). `mm check --base 0a605fbf`: `check: ok` (only pre-existing ADR "no Evidence" warnings).
No new `@Suppress`, no raw `catch` in the diff (CR-19b unchanged).

| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `RiassuntoPresenter.kt:296-303` `mostraErrore` extracts the "apply after `ricarica()`" step once; `:318-321`, `:413-416` read as the rule (re-read, then message; this Parte vs switch). |
| Naming | 4 | `lette`, `fermata`, `riprodotta`, `mostraErrore` are domain words, consistent with siblings. |
| Modularity | 4 | changes stay in the three `:ui` presenters; same helper `nonFataleRegistrato`, same lane `io` (`LettorePresenter.kt:143`). |
| Duplication | 5 | the inline message application of `riassumi` folded into `mostraErrore` and reused by 3 callers (`RiassuntoPresenter.kt:292`, `:320`, `:415`). |
| Concision | 4 | `leggiSenzaErrori` removed; `ImpostazioniPresenter.kt:28` keeps `lette` as a field for the presenter's lifetime though only init needs it (advice). |
| Error handling | 5 | every site follows non-fatal-faults "Message at every edge": logged WARNING, generic message, follow-up preserved (re-read `RiassuntoPresenter.kt:319`; `Preferenze()` fallback `ImpostazioniPresenter.kt:30`; switch still happens `:414`). `pausa` (`LettorePresenter.kt:140-148`) no longer escapes into `scope` and honours `richiestaCorrente` like `avvia` (`:128`). |
| Tests | 4 | one test per site, each checks log line (`causatoDa`), message and follow-up: `RiassuntoPresenterGuastiTest.kt:73-103`, `ImpostazioniPresenterTest.kt:151-174`, `LettorePresenterTest.kt:255-282` (also proves `scope` survives: a later `riproduci` reaches `Pronto`). Existing tests unchanged. |

## Blocking
None.

## Advice (non-blocking)
- `ImpostazioniPresenterTest.kt:151-169` also exercises `chiudiErrore` in the same test: two behaviours in one; a
  separate test would read more as a specification.
- `ImpostazioniPresenter.kt:28`: `lette` could be a local of an `init` block (or a pair returned by a small function),
  so the class does not carry a field used only at construction.
- `pausa` does not guard against a newer `pausa`/`riproduci` that is in flight but shares the same `richiestaCorrente`
  (a `pausa` does not bump it); harmless here since both outcomes are the same generic error, noted only.

## Conventions
- `.mismagent/conventions-proposals.md`: citation-only update for non-fatal-faults ("Shown by" gains `pausa`, the three
  sites) matches the diff.
- `.mismagent/design-notes.md`: the other-Parte Fonte case is the deferral the slice asked for, not a problem in the diff.
