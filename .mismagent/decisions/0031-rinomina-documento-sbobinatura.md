---
scope: global
status: accepted
supersedes: null   # partial, a rename only: every ADR up to 0030 keeps the old term `Documento` as history; read it as `Sbobinatura`. Amended in place: context-map.md, architecture.md, code-rules.md, infra-notes.md, dev-architecture-app.md.
closes_spike: null
decided: 2026-09-30 · user (feature `incontro` explore, D-0006: the rename ships on its own, before the feature; folder migrated) · Claude (mechanics)
enforced_by: []   # the old name is kept out of declarations by CR-10's forbidden synonyms (RegoleArchitetturaliTest), not by a script
---
# 0031 — `Documento` is renamed `Sbobinatura`

## Context
The `.md` file of a `Registrazione` is a transcript of who said what: in Italian, a *sbobinatura*. "Documento" says
nothing about it and was about to become ambiguous next to the `Riassunto` and the coming `Incontro`. The user asked to
call it for what it is (feature `incontro`, explore, 2026-09-30) and to ship the rename as its own change.

## Decision
1. **One concept, one name.** The ubiquitous-language term, the bounded context, the modules (`:sbobinatura:applicazione`,
   `:sbobinatura:adattatori`), the packages (`snastro.sbobinatura..`), the types (`Sbobinatura`, `ScrittoreSbobinatura`,
   `RigenerazioneSbobinaturaPolitica`, …) and the UI texts ("Apri sbobinatura") all use `Sbobinatura`. The meaning is
   unchanged: derived, regenerated, never a source, one per `Registrazione`.
2. **The project folder is `sbobinature/`.** When a project is opened, a pre-rename `documenti/` folder is renamed to
   `sbobinature/` once (`migraCartellaSbobinature`, `:avvio`). The files are derived and the startup sweep regenerates
   them anyway (AC-185), so nothing is merged: if both folders exist, both are left as they are.
3. **History is not rewritten.** ADRs 0001–0030 and the feature folders keep `Documento`. The names of the
   `controlli-adr` scripts and fixtures stay as the ADRs' `enforced_by` cite them (e.g. `adr-0010-documento-non-legge.sh`);
   their contents check the renamed module paths.
4. **The old name cannot come back.** `Documento` joins CR-10's forbidden synonyms, and the context-map entry lists it
   under "Not:".

## Rejected options
- **Rename the term only, keep the `:documento` module and packages.** It would leave two names for one concept in
  the code, which is exactly what CR-10 forbids.
- **Keep `documenti/` on disk.** The folder the user opens would carry the old name.

## Consequences
- Branches cut before this change (e.g. `feature/incontro`) rebase onto a renamed tree; their docs say `Documento`
  and are updated on rebase.
- The Git history of the moved files is followed with `git log --follow`.
