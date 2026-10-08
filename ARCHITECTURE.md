# snastro — Architecture

La mappa dell'architettura vive in `.mismagent/` e resta la fonte unica:
- [`.mismagent/architecture.md`](.mismagent/architecture.md) — stile, mappa dei moduli, dipendenze ammesse, confini, composizione, canali di enforcement;
- [`.mismagent/context-map.md`](.mismagent/context-map.md) — contesti e loro relazioni;
- [`.mismagent/code-rules.md`](.mismagent/code-rules.md) — regole del codice;
- [`.mismagent/decisions/`](.mismagent/decisions/) — ADR.

## Change log
- 2026-10-08 — acceptance tests move to *tests/acceptance/src/test/kotlin*, compiled as an extra test source dir of
  `:avvio` (conventions topic acceptance-tests; decided: codex, delegated by the user): mm reads `EX-<n>` markers only
  there, and a separate Gradle module would cost settings, module-map and allowed-edge changes. Refactor slice 06.
- 2026-10-08 — design pass (R0): `.mismagent/architecture.md` (R3 registry line) now says that `:avvio`'s
  `SessioneProgetto` also deletes a project — it said open/close only, the code does more since slices 01–03
  (`SessioneProgettoImpl.elimina`). No decision changed. Queued refactors 08–11: delete flow out of
  `ProgettiPresenter` (it sits at detekt's `TooManyFunctions` limit), acceptance-test scopes and the recursive
  EX-6 check, one shared `MessaggioInlineErrore` and one "More" menu in S4.
