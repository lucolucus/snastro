VERDICT: PASS
SHA: db74da1bd5b7d09cafaf7ed6677d42d1f874d1bf
SCORES: simple=5 naming=5 modularity=5 duplication=5 concision=5 errors=- tests=4

Slice 10-messaggio-inline-errore-condiviso (refactor, no examples). Base cbfc06c8..HEAD db74da1b.

## Evidence run
- `mm gate`: green (`./gradlew check`); `MessaggioInlineErroreTest` ran: tests=1, failures=0
  (`ui/build/test-results/test/TEST-snastro.ui.stile.MessaggioInlineErroreTest.xml`).
- `mm check --base cbfc06c8`: ok (only pre-existing ADR "no Evidence" warnings, outside the diff).
- No change under `ui/src/test/resources/` (render-check baselines untouched); no render-check test edited.
- No new suppression in the diff.

## Scores
| dimension | score | evidence |
|---|---|---|
| Simple design | 5 | `ui/src/main/kotlin/snastro/ui/stile/MessaggioInlineErrore.kt:14-37` is the old body verbatim, one composable, one job |
| Naming | 5 | name kept; KDoc at `stile/MessaggioInlineErrore.kt:13` states the `<tag>-chiudi` contract |
| Modularity | 5 | lives in `snastro.ui.stile`, `internal`, alongside `MenuAltreAzioni` (the slice-01 precedent); callers in `progetti/`, `registrazioni/`, `parlanti/` import it (`SchermataProgetti.kt:53`, `PannelloNuovoProgetto.kt:39`, `SchermataRegistrazioni.kt:91`, `SchermataParlanti.kt:64`) |
| Duplication | 5 | three identical copies removed (`PannelloNuovoProgetto.kt` -25, `SchermataRegistrazioni.kt` -25, `SchermataParlanti.kt` -25); grep of `ui/src/main` finds only the shared one plus the intentionally different private one in `registrazione/SchermataRegistrazione.kt:510` |
| Concision | 5 | now-unused imports removed (`clickable`, `ETICHETTA_CHIUDI_ERRORE` in `SchermataParlanti.kt`, `ETICHETTA_CHIUDI_ERRORE` in `PannelloNuovoProgetto.kt`); the one kept in `SchermataRegistrazioni.kt:99` is still used at :271/:281 |
| Errors | - | not touched |
| Tests | 4 | `ui/src/test/kotlin/snastro/ui/stile/MessaggioInlineErroreTest.kt:18-27`: one behaviour, Compose seam, checks text and that `<tag>-chiudi` calls `onChiudi`. Committed first against the old `snastro.ui.progetti` copy (d5d3574d), then only its import changed (e474cb7c) — a true characterization |

## Findings
Blocking: none.

Advice (non-blocking):
- `registrazione/SchermataRegistrazione.kt:510` keeps a private `MessaggioInlineErrore(messaggio, onChiudi)` with the same
  name as the shared one. It does not clash today (different package, no import of the shared one there), so the slice
  rightly left it alone; renaming it to what it is (e.g. the S3-specific error line) would remove the ambiguity for readers.
- The test name mixes two observations (text shown, close calls back); acceptable for a characterization of one component.
- No proposal needed in `conventions-proposals.md`: the pattern (shared UI component in `stile/`) is not first of its kind.
