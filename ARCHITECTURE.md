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
