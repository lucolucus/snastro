VERDICT: PASS
SHA: 5d8defd7047e3f9d1a12cdeaaed2e94f0e9dc83f
SCORES: simple=5 naming=5 modularity=5 duplication=5 concision=5 errors=- tests=4

| Dimension | Score | Evidence |
|---|---|---|
| simple | 5 | `ui/src/main/kotlin/snastro/ui/parlanti/SchermataParlanti.kt:297-319` — local state, FocusRequester and Box gone; one call to `MenuAltreAzioni` with two items. |
| naming | 5 | `SchermataParlanti.kt:298` `val id = riga.parlanteId` — short local, intent clear; tags unchanged (`parlanti-altre-azioni-/promuovi-/elimina-<id>`). |
| modularity | 5 | Reuses `ui/src/main/kotlin/snastro/ui/stile/MenuAltreAzioni.kt:19` (internal, same module); no new public surface. |
| duplication | 5 | The second hand-written More button + DropdownMenu + L742b focus-return is removed (diff -35/+22); one "More" menu now in `stile/`. |
| concision | 5 | Net -13 lines; imports `DropdownMenu` and `ETICHETTA_ALTRE_AZIONI` removed with their use. |
| errors | - | No error path touched. |
| tests | 4 | No test edited (diff touches no test file). Behaviour kept: `ParlantiVistaTest` 4/4 and `ParlantiRenderCheckTest` 24/24 green in the gate run at this HEAD. Refactor, no new examples. |

## Behaviour equivalence
- Tag `parlanti-altre-azioni-${id.valore}` and `abilitato = !riga.operazioneInCorso` passed exactly as before (`SchermataParlanti.kt:299`).
- Promuovi only for `OCCASIONALE`, Elimina in `danger`; each item calls `chiudi()` before its action, same order as before (`SchermataParlanti.kt:300-318`).
- Dismissal (`onDismissRequest`) and focus return now come from `MenuAltreAzioni.kt:27-37`, identical code to the removed block.

## Checks
- `MM gate` (./gradlew check): green. `MM check --base 0a63f0c4`: ok.
- No new suppression: `@file:Suppress("TooManyFunctions")` at `SchermataParlanti.kt:3` predates the diff.
- Nothing first-of-its-kind: no conventions proposal needed.

## Findings
None blocking. No advice.
