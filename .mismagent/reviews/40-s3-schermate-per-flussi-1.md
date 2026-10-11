VERDICT: PASS
SHA: 4742b1b9198d2725c0ba769b636c22222ad44a43
SCORES: simple=4 naming=4 modularity=4 duplication=4 concision=4 errors=- tests=-

# Review: 40-s3-schermate-per-flussi (round 1)

Base e596654b..HEAD 4742b1b9. `mm gate`: green (check + detekt). `mm check --base e596654b`: ok (only the
pre-existing ADR "no Evidence" warnings). No test file in the diff (`git diff --stat e596654b..HEAD -- '*Test*'
ui/src/test` is empty), so the `Registrazione*RenderCheckTest` suites and the test tags keep their assertions. The slice
has no examples (refactor).

## Moves only: verified
I took the two Base files (`git show e596654b:…/SchermataRegistrazione.kt`, `…/SchermataPannelloVoci.kt`) and the seven
HEAD files, dropped `package`/`import` lines and leading `private `/`internal `, stripped indentation, then sorted and
diffed the lines. The only differences are:
- the toolbar extraction: `MenuRiassegna` and `BottoneDividi` (`BarraSelezioneVista.kt:96-123`) hold the `MenuVoci(...)`
  and `BottoneSn(...)` calls moved from the Base toolbar body unchanged, plus their two KDoc lines and two call sites
  (`BarraSelezioneVista.kt:70-71`);
- the removed suppressions: Base's file-level `TooManyFunctions` on both files and the toolbar's `LongMethod`, with their
  reason comments;
- the new reasoned file-level suppression at `CartaVoceVista.kt:1-3`;
- one comment edit ("in `CartaVoceVista` below" -> "in `CartaVoceVista`").

No logic, tag, string or modifier changed.

## Scores (diff)
| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `SchermataRegistrazione.kt` 798 -> 335 lines and `SchermataPannelloVoci.kt` 899 -> 185. Both keep the entry and orchestration as screen-split requires. `BarraSelezioneVista.kt:45` no longer needs `LongMethod`. |
| Naming | 4 | Files are named after their main composable (`SegmentoItem.kt:64`, `IntestazioneRegistrazione.kt:50`, `BarraSelezioneVista.kt:45`, `CartaVoceVista.kt:71`). `MenuRiassegna`/`BottoneDividi` follow the screen's `Menu*`/`Bottone*` vocabulary. |
| Modularity | 4 | Each `internal` is used from another file: `BriciolaParte`/`SelettoreParte` from `SchermataRegistrazione.kt:122,124`; `EtichettaGruppoMenu`, `EtichettaMenu`, `EtichettaMenuVoce` and `etichettaTipo` from `CartaVoceVista.kt:243,451,454,494`. Everything else stays `private`. The one deviation from the slice text is that `AzioniFrase` lives in `BarraSelezioneVista.kt:127`, not `SegmentoItem.kt`. The rule supports this: the toolbar is its only caller, so it stays `private`. The builder recorded it in progress.md. |
| Duplication | 4 | Nothing is duplicated. The moved menus exist once, in `MenuParlanti.kt`. |
| Concision | 4 | No dead code. Base's unneeded suppressions are gone. Only `CartaVoceVista.kt:3` keeps a reasoned `TooManyFunctions` (detekt counts 11 functions against a threshold of 11, per progress.md). |
| Error handling | - | not touched |
| Test quality | - | not touched |

## Conventions
The screen-split rule (`.claude/skills/conventions/references/screen-split.md`) is followed:
- same-package files named after their main composable;
- `internal` only across files;
- one reasoned residual suppression.

The proposal `update screen-split` in `.mismagent/conventions-proposals.md` lists exactly the six S3 files of the diff,
with correct qualifiers. No stale path to the moved code is left in `ARCHITECTURE.md`, the skills or the `.mismagent/*.md`
docs. The design note on `MenuAltreAzioniCarta` was repointed to `CartaVoceVista.kt`.

## Blocking findings
None.

## Advice (not blocking)
- `CartaVoceVista.kt:2` says "over the threshold by one". detekt reports 11 functions against a threshold of 11, so the
  file is *at* the threshold. Moving `Indicatore` (a two-line helper) or `VociDaUnire` next to its only caller's flow
  would remove the last suppression. That is optional.
- I could not reproduce the smoke byte-identity independently. `./gradlew :avvio:run --args=--smoke` from `git archive`
  copies of Base and HEAD fails with `CartellaNonTrovata(build/smoke-fixture)`: the fixture project is not in the repo,
  and `profile.md:34` does not say how to make it. The verbatim-move diff above is the stronger evidence for a moves-only
  slice. The builder's `Smoke.kt:151` race note is already in design-notes.md.
