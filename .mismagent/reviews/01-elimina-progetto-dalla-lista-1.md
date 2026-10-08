VERDICT: REWORK
SHA: 3a6559c71006972b7b6b77448c634e61cddea79e
SCORES: simple=4 naming=5 modularity=4 duplication=3 concision=4 errors=4 tests=4

# Review — 01-elimina-progetto-dalla-lista (slice, base 22db6f14)

Gate: `mm gate` green (`./gradlew check` + `./gradlew detekt`); `mm check --base 22db6f14` ok.
`EliminaProgettoDallaListaTest` 3/3 green, `SchermataProgettiEliminaTest`, `ProgettiPresenterTest`, the
`SessioneProgettoContratto` test (run by `SessioneProgettoImplTest` and `SessioneProgettoFintaTest`) and
`ProgettiRenderCheckTest` all green.

| dimension | score | evidence |
|---|---|---|
| Simple design | 4 | `ProgettiPresenter.kt:98-106` confermaElimina: one guard, one `avvia`; `PannelloNuovoProgetto.kt` `PannelloSuVelo` extraction made `LongMethod` unnecessary |
| Naming | 5 | `ConfermaEliminazione.confermataDa`, `cancellaCartella`, `chiediElimina/annullaElimina/confermaElimina`: domain words, read without comments |
| Modularity | 4 | `SessioneProgetto.elimina` port in `:ui`, impl in `:avvio` (`SessioneProgettoImpl.kt:197`); `java.nio.file` stays in `:avvio` (CR-2) |
| Duplication | 3 | `EliminaProgetto.kt:39-61` `MenuProgetto` is a fourth copy of the "⋯" + `DropdownMenu` component (see B1) |
| Concision | 4 | nothing unused; `AZIONI_VUOTE` duplicated 4× in tests is now one fixture (`AzioniProgettiVuote.kt`) |
| Errors | 4 | I/O faults are exceptions (ADR 0003: infra), caught once by `avvia`'s boundary into `MESSAGGIO_ERRORE_GENERICO` inside the confirmation; tested (`ProgettiPresenterTest` "un elimina che lancia…") |
| Tests | 4 | one behaviour each, named as the example, at the presenter seam over the real `SessioneProgettoImpl` and real folders |

## Examples

- **EX-1** — `EliminaProgettoDallaListaTest.kt` `EX-1 …`: three real projects created and closed, Elimina on
  "test", typed `test`, confirmed → `test.snastro` gone, list = {skatefest, mangu 2}. Same given/when/then. Proven.
- **EX-2** — `EX-2 …`: for `Test` and `tes`, `confermataDa` (the exact function the view's button uses,
  `EliminaProgetto.kt:111`) is false and `confermaElimina` does nothing; folder and list intact. The disabled
  button itself is proven in `SchermataProgettiEliminaTest` ("tes" → not enabled). Proven.
- **EX-3** — `EX-3 …`: Elimina then Annulla → folder intact, list unchanged, confirmation closed. Proven.
- No test of another example was edited (the four test files that lost `AZIONI_VUOTE` only moved it to a shared fixture).

## Blocking

**B1 — Duplication (3 < 4) and "a second way": the "⋯" overflow menu.**
`ui/src/main/kotlin/snastro/ui/progetti/EliminaProgetto.kt:39-61` (`MenuProgetto`) re-writes the "⋯" button +
`DropdownMenu` that already exists, with a content slot, as `MenuAltreAzioni`
(`ui/src/main/kotlin/snastro/ui/registrazioni/SchermataRegistrazioni.kt:752-774`), and again in
`parlanti/SchermataParlanti.kt:~300-320` and `registrazione/SchermataPannelloVoci.kt:109`. The new copy also
diverges from the established behaviour: both precedents return focus to the "⋯" button when the menu closes
(L742b, `SchermataRegistrazioni.kt:758`, `SchermataParlanti.kt:301`); `MenuProgetto` does not
(`onDismissRequest = { espanso = false }`, `EliminaProgetto.kt:50`). The implementer saw this and wrote it to
`.mismagent/design-notes.md` line 3 — but a problem *in* the diff is not a design note.
**Fix:** lift `MenuAltreAzioni(tag, abilitato, contenuto)` from `SchermataRegistrazioni.kt` to a shared
`internal` component (e.g. `snastro.ui.stile`), use it in `MenuProgetto` (one `DropdownMenuItem` "Elimina…" in the
slot, calling `chiudi()` then `chiediElimina`), and drop `MenuProgetto` from the design note (the other copies —
parlanti, pannello voci, `MessaggioInlineErrore` — may stay there as out-of-diff notes).

## Advice (not blocking)

- `SessioneProgettoImpl.kt:197-200`: no check that `percorso` is a snastro project folder before a full recursive
  delete; today registry entries only come from `crea`/`apri`, and slice 02 (EX-9) restricts deletion to
  app-created content — make sure slice 02 adds that guard, since the current walk deletes anything under the path.
- `SessioneProgettoImpl.kt:198-199`: folder deleted before the registry entry; a failing `rimuovi` leaves a stale
  entry. Correctly deferred (design note 2) to the "Cartella non trovata" / partial-outcome slices.
- `ProgettiPresenter.kt:69` `senzaErroriDiApertura` as a private `val` lambda: a private fun reference would read
  more plainly. Taste.
- `elimina` takes no `.lock` yet (EX-6, slices 03/04): until then a project open in another window can be deleted
  from this one. Noted in `progress.md`.

## Conventions proposals

`.mismagent/conventions-proposals.md` holds two proposals (acceptance-test location; confirm-by-typing); both match
the diff (`avvio/src/test/kotlin/snastro/avvio/accettazione/EliminaProgettoDallaListaTest.kt`;
`ConfermaEliminazione.confermataDa` used by `EliminaProgetto.kt:111` and `ProgettiPresenter.kt:100`).
